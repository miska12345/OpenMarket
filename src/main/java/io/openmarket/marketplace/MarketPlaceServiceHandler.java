package io.openmarket.marketplace;

import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.grpc.MarketPlaceProto;
import io.openmarket.marketplace.grpc.MarketPlaceProto.GetOrgItemsRequest;
import io.openmarket.marketplace.grpc.MarketPlaceProto.GetOrgItemsResult;
import io.openmarket.marketplace.grpc.MarketPlaceProto.MarketPlaceItem;
import io.openmarket.marketplace.model.Item;
import io.openmarket.order.dao.OrderDao;
import io.openmarket.order.model.ItemInfo;
import io.openmarket.order.model.Order;
import io.openmarket.order.model.OrderStatus;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.model.Organization;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.service.TransactionServiceHandler;
import io.openmarket.utils.TimeUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Log4j2
public class MarketPlaceServiceHandler {
    private final ItemDao itemDao;
    private final OrderDao orderDao;
    private final OrgServiceHandler orgServiceHandler;
    private final TransactionServiceHandler transactionServiceHandler;

    @Inject
    public MarketPlaceServiceHandler(@Nonnull final ItemDao itemDao,
                                     @NonNull final OrderDao orderDao,
                                     @NonNull final OrgServiceHandler orgServiceHandler,
                                     @Nonnull final TransactionServiceHandler transactionServiceHandler) {
        this.itemDao = itemDao;
        this.orderDao = orderDao;
        this.orgServiceHandler = orgServiceHandler;
        this.transactionServiceHandler = transactionServiceHandler;
        log.info("MarketPlaceServiceHandler started");
    }

    public GetOrgItemsResult getListingByOrgId(GetOrgItemsRequest request) throws SQLException {
        List<Integer> itemIds = this.itemDao.getItemIdsByOrg(request.getOrgId());
        List<Integer> failedItemIds = new ArrayList<>();
        List<Item> selling = this.itemDao.batchLoad(itemIds, failedItemIds);
        List<MarketPlaceItem> result = new ArrayList<>();

        for(Item item : selling) {
            result.add(convertToGrpc(item));
        }

        return GetOrgItemsResult.newBuilder().addAllItems(result).build();
    }

    public MarketPlaceProto.CheckOutResult checkout(@NonNull final String userId,
                                                    @NonNull final MarketPlaceProto.CheckOutRequest request) {
        log.info("User {} requested checkout with request: {}", userId, request);

        // TODO: Modify error type
        if (!isCheckOutRequestValid(request)) {
            log.error("User {} check out with invalid request {}", userId, request);
            return MarketPlaceProto.CheckOutResult.newBuilder()
                    .setError(MarketPlaceProto.Error.INTERNAL_SERVICE_ERROR)
                    .build();
        }

        final List<MarketPlaceProto.Order> successOrders = new ArrayList<>();
        final Map<Integer, MarketPlaceProto.FailedCheckOutCause> failedItems = new HashMap<>();
        final List<Integer> batchFailedItemIds = new ArrayList<>();
        try {
            // Fetch all items in a batch, then filter out the out of stock ones.
            List<Item> itemsList = itemDao.batchLoad(request.getItemsMap().keySet(), batchFailedItemIds);
            itemsList = splitOutOfStockItems(itemsList, failedItems, request.getItemsMap());
            addAllInvalidItems(batchFailedItemIds, failedItems);

            // A map of orgId to list of items sold by the particular org.
            final Map<String, List<Item>> orgIdToItemsMap = mapOrgIdToItems(itemsList);

            // Process each organization's order separately in a batch.
            for (String orgId : orgIdToItemsMap.keySet()) {
                TransactionServiceHandler.Stepper stepper = null;
                try {
                    final Organization organization = orgServiceHandler.getOrg(orgId).orElseThrow(
                            () -> new IllegalArgumentException(String.format("OrgId %s is invalid", orgId)));
                    final Order order = generateOrderFromCheckOutItems(userId, organization, orgIdToItemsMap.get(orgId),
                            request.getItemsMap());
                    stepper = transactionServiceHandler.createPaymentStepper(userId,
                            TransactionProto.PaymentRequest.newBuilder()
                                    .setType(TransactionProto.PaymentRequest.Type.PAY)
                                    .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
                                            .setCurrencyId(order.getCurrency())
                                            .setAmount(order.getTotal())
                                            .build())
                                    .setRecipientId(organization.getOrgName())
                                    .setNote(String.format("Order %s", order.getOrderId()))
                                    .build());
                    log.debug("Generated transaction stepper with transactionId {}", stepper.getTransaction().getTransactionId());
                    order.setTransactionId(stepper.getTransaction().getTransactionId());
                    log.info("New order generated: {}", order);
                    orderDao.save(order);
                    stepper.commit();
                    successOrders.add(convertOrderModelToGrpcOrder(order));
                } catch (IllegalArgumentException e) {
                    orgIdToItemsMap.get(orgId).forEach(x -> failedItems.put(x.getItemID(),
                            MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST));
                    log.error("User {} requested check out invalid item with orgId {}, request: {}", userId,
                            orgId, request);
                } finally {
                    if (stepper != null) {
                        stepper.abort();
                    }
                }
            }
            log.info("Finished processing checkout for user {}, success: {}, failed: {}", userId,
                    successOrders.size(), failedItems.size());
            return MarketPlaceProto.CheckOutResult.newBuilder()
                    .setError(MarketPlaceProto.Error.NONE)
                    .addAllSuccessOrders(successOrders)
                    .putAllFailedItems(failedItems)
                    .build();
        } catch (IllegalStateException e) {
            log.error("User {} triggered internal service error with request {}", userId, request);
            return MarketPlaceProto.CheckOutResult.newBuilder()
                    .setError(MarketPlaceProto.Error.INTERNAL_SERVICE_ERROR)
                    .build();
        }
    }

    private static boolean isCheckOutRequestValid(MarketPlaceProto.CheckOutRequest request) {
        return request.getItemsMap() != null;
    }

    private static void addAllInvalidItems(@NonNull final Collection<Integer> failedItemIds, @NonNull final Map<Integer,
            MarketPlaceProto.FailedCheckOutCause> failedItems) {
        failedItemIds.forEach(a -> failedItems.put(a, MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST));
    }

    private static List<Item> splitOutOfStockItems(@NonNull final Collection<Item> src, @NonNull final Map<Integer,
            MarketPlaceProto.FailedCheckOutCause> failedItems,
                                                   @NonNull final Map<Integer, Integer> itemIdToQuantityMap) {
        final List<Item> validItems = new ArrayList<>();
        src.forEach(a -> {
            if (a.getStock() <= 0 || a.getStock() < itemIdToQuantityMap.get(a.getItemID())) {
                failedItems.put(a.getItemID(), MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK);
            } else {
                validItems.add(a);
            }
        });
        return validItems;
    }

    private static Order generateOrderFromCheckOutItems(@NonNull final String userId,
                                                        @NonNull final Organization organization,
                                                        @NonNull final Collection<Item> items,
                                                        @NonNull final Map<Integer, Integer> orderItems) {
        final String currentDate = TimeUtils.formatDate(new Date());
        final double totalCost = calculateTotalCost(items, orderItems);
//        if (totalCost < 0) {
//            log.error("Negative total cost {} detected, items: {}, orderItems: {}", totalCost, items, orderItems);
//            throw new IllegalArgumentException(String.format("Invalid total cost %f detected", totalCost));
//        }
        return Order.builder()
                .orderId(generateUniqueOrderId())
                .currency(organization.getOrgCurrency())
                .buyerId(userId)
                .sellerId(organization.getOrgName())
                .status(OrderStatus.PENDING_PAYMENT)
                .total(totalCost)
                .items(items.stream().map(a -> ItemInfo.builder()
                        .itemId(a.getItemID())
                        .price(a.getItemPrice())
                        .itemName(a.getItemName())
                        .quantity(orderItems.get(a.getItemID()))
                        .build()).collect(Collectors.toList())
                )
                .createdAt(currentDate)
                .lastUpdatedAt(currentDate)
                .build();
    }

    private static double calculateTotalCost(Collection<Item> items, Map<Integer, Integer> orderItems) {
        double total = 0.0;
        for (Item item : items) {
            total += item.getItemPrice() * orderItems.get(item.getItemID());
        }
        return total;
    }

    private static Map<String, List<Item>> mapOrgIdToItems(final List<Item> items) {
        final Map<String, List<Item>> result = new HashMap<>();
        for (Item item : items) {
            final List<Item> orgCheckOutItems = result.computeIfAbsent(item.getBelongTo(), k -> new ArrayList<>());
            orgCheckOutItems.add(item);
        }
        return result;
    }

    private static String generateUniqueOrderId() {
        return UUID.randomUUID().toString();
    }

    private static MarketPlaceProto.Order convertOrderModelToGrpcOrder(final Order order) {
        return MarketPlaceProto.Order.newBuilder()
                .setOrderId(order.getOrderId())
                .setBuyerId(order.getBuyerId())
                .setSellerId(order.getSellerId())
                .setCurrency(order.getCurrency())
                .setTotalAmount(order.getTotal())
                .addAllItems(
                        order.getItems().stream().map(a -> MarketPlaceProto.OrderedItem.newBuilder()
                                .setItemId(a.getItemId())
                                .setItemName(a.getItemName())
                                .setItemPrice(a.getPrice())
                                .setQuantity(a.getQuantity())
                                .build()).collect(Collectors.toList())
                )
                .setTransactionId(order.getTransactionId())
                .setStatus(MarketPlaceProto.OrderStatus.valueOf(order.getStatus().toString()))
                .setLastUpdatedAt(order.getLastUpdatedAt())
                .setCreatedAt(order.getCreatedAt())
                .build();
    }

//    public AddItemResult addItem(AddItemRequest request) {
//        ItemGrpc item = request.getItem();
//        if (!validateItemGrpc(item)) throw new IllegalArgumentException("Invalid add item request");
//
//        Item itemDb = Item.builder().itemName(item.getItemName())
//                .itemCategory(item.getCategory())
//                .itemDescription(item.getItemDescription())
//                .itemImageLink(item.getItemImageLink())
//                .itemPrice(item.getItemPrice())
//                .stock(item.getItemStock())
//                .belongTo(item.getBelongTo())
//                .purchasedCount(0)
//                .build();
//        this.itemDao.save(itemDb);
//        log.info("Created item with name {}, price {}, stock {}", item.getItemName()
//                , item.getItemPrice(), item.getItemStck());
//        return AddItemResult.newBuilder()
//                .setItemName(item.getItemName()).setAddStatus(AddItemResult.Status.SUCCESS).build();
//    }

    private boolean validateItemGrpc(MarketPlaceItem target) {
        if (target.getItemName().isEmpty() || target.getCategory().isEmpty()
            || target.getItemPrice() < 0 || target.getBelongTo().isEmpty()
            || target.getItemImageLink().isEmpty() || target.getItemStock() < 1){
            return false;
        }

        return true;
    }

    private MarketPlaceItem convertToGrpc(Item item) {
        return MarketPlaceItem.newBuilder()
                .setItemName(item.getItemName()).setItemStock(item.getStock())
                .setBelongTo(item.getBelongTo())
                .setItemPrice(item.getItemPrice())
                .setItemDescription(item.getItemDescription())
                .setItemId(String.valueOf(item.getItemID()))
                .setCategory(item.getItemCategory())
                .setItemImageLink(item.getItemImageLink())
                .build();
    }

//    public GetSimilarItemsResult getSimilarItem(GetSimilarItemsRequest request) {
//        if (request.getItemIds().isEmpty() || request.getItemCategory().isEmpty())
//            throw new IllegalArgumentException("Invalid get item requests");
//
//        String itemid = request.getItemIds();
//        if (!this.itemDao.load(itemid).isPresent()) throw new IllegalArgumentException("Invalid item id");
//        log.info("Getting similar items for {}", itemid);
//        String category = request.getItemCategory();
//        int withCount = request.getWithCount();
//
//        //+1 to handle to the case when the item itself is in the return result
//        List<String> ids = this.itemDao.getItemIdByCategory(category, withCount + 1);
//        List<Item> similarItem = this.itemDao.batchLoad(ids);
//        GetSimilarItemsResult.Builder response = GetSimilarItemsResult.newBuilder();
//
//        for(Item item : similarItem) {
//            if (!item.getItemID().equals(itemid)) {
//                response.addItems(convertToGrpc(item));
//            }
//        }
//
//        return response.build();
//    }
//
//    public CheckOutResult checkout(String uesrId, CheckOutRequest request) {
//        List<ItemGrpc> items = request.getItemsList();
//        double total = 0.0;
//        if (items.isEmpty()
//        || request.getCurrencyId().isEmpty() || request.getFromOrg().isEmpty())
//            throw new IllegalArgumentException("Invalid order");
//
//
//        log.info("Checkout received from {} buying from {}",
//                uesrId, request.getFromOrg());
//
//        //puts item on hold, not finalized until transcation is processed correctly
//       CheckOutResult result = updateItemDB(items, null);
//
//       if (result.getUnprocessedItemCount() == items.size()) return result;
//
//       Set<ItemGrpc> unprocessed = new HashSet<>(result.getUnprocessedItemList());
//       for (int i = 0; i < items.size(); i++) {
//           ItemGrpc item = items.get(i);
//
//           if (!unprocessed.contains(item)) {
//               Item dbItem = this.itemDao.load(item.getItemId()).get();
//               total += dbItem.getItemPrice() * item.getItemCount();
//           }
//       }
//
//        String transactionId = transactionServiceHandler.createPayment(uesrId,
//                TransactionProto.PaymentRequest.newBuilder()
//                        .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
//                        .setRecipientId(request.getFromOrg())
//                        .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
//                                .setCurrencyId(request.getCurrencyId())
//                                .setAmount(total)
//                                .build())
//                        .build()
//        );
//
//        if(!checkTransactionStatus(transactionId)) {
//            log.error("Transaction timed out");
//            //revert item from on hold to available again
//            updateItemDB(items, unprocessed);
//            return CheckOutResult.newBuilder()
//                    .setCheckoutStatus(CheckOutResult.Status.FAIL_TRANSACTION_TIME_OUT)
//                    .addAllUnprocessedItem(items)
//                    .build();
//        }
//        log.info("Checkout payment verifield from {}", uesrId);
//
//        return result;
//    }
//
//    private boolean checkTransactionStatus(String transactionId) {
//        for (int i = 0; i < CHECKOUT_TIMEOUT; i++) {
//            TransactionStatus paymentStatus = transactionServiceHandler.getTransactionStatus(transactionId);
//            if (paymentStatus.equals(TransactionStatus.COMPLETED)){
//                return true;
//            }
//            try{ Thread.sleep(CHECKOUT_QUERY_DELAY); }
//            catch (Exception e) { log.error(e.getStackTrace()); }
//        }
//        return false;
//    }

//    private CheckOutResult updateItemDB(List<ItemGrpc> items, Set<ItemGrpc> unprocessed) {
//        CheckOutResult.Builder response = CheckOutResult.newBuilder();
//
//        for(int i = 0; i < items.size(); i++) {
//            ItemGrpc item = items.get(i);
//            String updateExpression = "SET #count = #count - :hold, #purchaseCount = #purchaseCount + :val";
//            String conditionExpression = "#count >= :hold";
//
//            if (!this.itemDao.load(item.getItemId()).isPresent()) {
//                response.addUnprocessedItem(item);
//                continue;
//            }
//            else if (unprocessed != null && !unprocessed.contains(item)) {
//                updateExpression = "SET #count = #count + :hold, #purchaseCount = #purchaseCount - :val";
//                conditionExpression = null;
//            }
//
//            final UpdateItemRequest updateRequest = new UpdateItemRequest().withTableName(ITEM_DDB_TABLE_NAME)
//                    .withKey(ImmutableMap.of(ITEM_DDB_ATTRIBUTE_ID, new AttributeValue(item.getItemId())))
//                    .withUpdateExpression(updateExpression)
//                    .withConditionExpression(conditionExpression)
//                    .withExpressionAttributeNames(
//                            ImmutableMap.of("#count", ITEM_DDB_ATTRIBUTE_STOCK,
//                            "#purchaseCount", ITEM_DDB_ATTRIBUTE_PURCHASE_COUNT)
//                    ).withExpressionAttributeValues(
//                            ImmutableMap.of(":hold", new AttributeValue().withN(String.valueOf(item.getItemCount())),
//                                            ":val", new AttributeValue().withN("1")
//                                    //TODO change the constant string 1 to itemCount
//                            )
//                    );
//
//            try {
//                this.itemDao.update(updateRequest);
//            } catch (ConditionalCheckFailedException e) {
//                log.error("Item {} with id {} out of stock", item.getItemName(), item.getItemId());
//                response.setCheckoutStatus(CheckOutResult.Status.SUCESS_WITH_FEW_ITEM_OUT_OF_STOCK);
//                response.addUnprocessedItem(item);
//            }
//        }
//
//        if (response.getUnprocessedItemCount() == items.size()) {
//            log.info("None of the item is in stock, try again later");
//            response.setCheckoutStatus(CheckOutResult.Status.FAIL_ITEM_OUT_OF_STOCK);
//            return response.build();
//        }
//        else if (response.getCheckoutStatus().equals(CheckOutResult.Status.SUCESS_WITH_FEW_ITEM_OUT_OF_STOCK)) {
//            log.info("Succesfully checked out {} item, with {} item out of stock",
//                    items.size() - response.getUnprocessedItemCount(), response.getUnprocessedItemCount());
//            return response.build();
//        }
//
//        log.info("Succesfully checked out {} item", items.size());
//        return response.setCheckoutStatus(CheckOutResult.Status.SUCCESS).build();
//    }

}
