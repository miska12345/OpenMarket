package io.openmarket.marketplace;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.google.common.annotations.VisibleForTesting;
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
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.service.TransactionServiceHandler;
import io.openmarket.utils.MiscUtils;
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

    private static final int CHECKOUT_PAYMENT_MAX_ATTEMPTS = 10;
    private static final int CHECKOUT_PAYMENT_PERIOD = 1000;

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

    public MarketPlaceProto.GetAllOrdersResult getOrders(@NonNull final String userId,
                                                         @NonNull final MarketPlaceProto.GetAllOrdersRequest request) {
        if (!isGetOrderRequestValid(request)) {
            log.warn("User {} issued an invalid get order request: {}", userId, request);
            throw new IllegalArgumentException("Invalid get order request");
        }
        final List<String> orderIds = new ArrayList<>();
        Map<String, AttributeValue> exclusiveStartKey = MiscUtils.getExclusiveStartKey(request.getExclusiveStartKey())
                .orElse(null);
        if (request.getRole().equals(MarketPlaceProto.Role.BUYER)) {
            log.info("HEre");
            exclusiveStartKey = orderDao.getOrderByBuyer(userId, exclusiveStartKey, orderIds, request.getMaxCount());
        } else {
            exclusiveStartKey = orderDao.getOrderBySeller(userId, exclusiveStartKey, orderIds, request.getMaxCount());
        }

        final List<Order> orders = orderDao.batchLoad(orderIds);
        log.info("User {} got {} for request {}", userId, orders, request);
        return MarketPlaceProto.GetAllOrdersResult.newBuilder()
                .addAllOrders(orders.stream().map(MarketPlaceServiceHandler::convertOrderModelToGrpcOrder)
                        .collect(Collectors.toList()))
                .setLastEvaluatedKey(MiscUtils.convertToLastEvaluatedKey(exclusiveStartKey))
                .build();
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
        final List<MarketPlaceProto.Order> actionRequiredOrders = new ArrayList<>();
        try {
            // Fetch all items in a batch, then filter out the invalid ones.
            List<Item> itemsList = itemDao.batchLoad(request.getItemsMap().keySet(), batchFailedItemIds);
            moveAllInvalidItems(batchFailedItemIds, failedItems, itemsList,
                    MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST);

            // A map of orgId to list of items sold by the particular org.
            final Map<String, List<Item>> orgIdToItemsMap = mapOrgIdToItems(itemsList);

            // Process each organization's order separately in a batch.
            for (Map.Entry<String, List<Item>> itemsToCheckOut : orgIdToItemsMap.entrySet()) {
                try {
                    final Organization organization = orgServiceHandler.getOrg(itemsToCheckOut.getKey()).orElseThrow(
                            () -> new IllegalArgumentException(String.format("OrgId %s is invalid",
                                    itemsToCheckOut.getKey())));

                    itemsList = itemsToCheckOut.getValue();

                    // A map of itemID to quantity.
                    final Map<Integer, Integer> polishedRequest = itemsToCheckOut.getValue().stream()
                            .collect(Collectors.toMap(Item::getItemID, a -> request.getItemsMap().get(a.getItemID())));

                    // Avoids unwanted throttling by using a temporary order to check if user has enough balance.
                    Order order = generateOrderFromCheckOutItems(userId, organization,
                            itemsList, request.getItemsMap());
                    if (transactionServiceHandler.getBalanceForCurrency(userId, order.getCurrency()) < order.getTotal()) {
                        log.info("User {} doesn't have enough {} to check out order {}", userId, order.getCurrency(), itemsList);
                        moveAllInvalidItems(itemsList.stream().map(Item::getItemID).collect(Collectors.toList()),
                                failedItems, itemsList,
                                MarketPlaceProto.FailedCheckOutCause.INSUFFICIENT_BALANCE);
                        continue;
                    }

                    final List<Integer> updateFailedItemIds = itemDao.updateItemStock(polishedRequest);
                    moveAllInvalidItems(updateFailedItemIds, failedItems, itemsList,
                            MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK);

                    if (itemsList.isEmpty()) {
                        log.info("All items are out of stock or invalid. UpdatedFailedItems: {}",
                                updateFailedItemIds);
                        continue;
                    }
                    order = generateOrderFromCheckOutItems(userId, organization,
                            itemsList, request.getItemsMap());
                    final TransactionServiceHandler.Stepper stepper = transactionServiceHandler.createPaymentStepper(userId,
                            TransactionProto.PaymentRequest.newBuilder()
                                    .setType(TransactionProto.PaymentRequest.Type.PAY)
                                    .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
                                            .setCurrencyId(order.getCurrency())
                                            .setAmount(order.getTotal())
                                            .build())
                                    .setRecipientId(organization.getOrgName())
                                    .setNote(String.format("Order %s", order.getOrderId()))
                                    .build()
                            );
                    final String transactionId = stepper.getTransaction().getTransactionId();
                    order.setTransactionId(transactionId);
                    order.setStatus(OrderStatus.PENDING_PAYMENT);
                    orderDao.save(order);
                    stepper.commit();

                    // Wait for payment to complete.
                    final TransactionStatus status = getPaymentStatus(transactionId);
                    log.info("The status for transaction {} is {}", transactionId, status);
                    switch (status) {
                        case PENDING:
                            actionRequiredOrders.add(convertOrderModelToGrpcOrder(order));
                            log.warn("Transaction {}'s status for order {} is pending",
                                    order.getTransactionId(), order.getOrderId());
                            break;
                        case COMPLETED:
                            successOrders.add(convertOrderModelToGrpcOrder(order));
                            break;
                        case ERROR:
                            moveAllInvalidItems(itemsList.stream().map(Item::getItemID).collect(Collectors.toList()),
                                    failedItems, itemsList,
                                    MarketPlaceProto.FailedCheckOutCause.INSUFFICIENT_BALANCE);
                            break;
                        default:
                            log.error("Unhandled payment status {}", status);
                    }
                } catch (IllegalArgumentException e) {
                    orgIdToItemsMap.get(itemsToCheckOut.getKey()).forEach(x -> failedItems.put(x.getItemID(),
                            MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST));
                    log.error("User {} requested check out invalid item with orgId {}, request: {}", userId,
                            itemsToCheckOut.getKey(), request);
                }
            }
            log.info("Finished processing checkout for user {}, success: {}, failed: {}, action: {}", userId,
                    successOrders.size(), failedItems.size(), actionRequiredOrders.size());
            return MarketPlaceProto.CheckOutResult.newBuilder()
                    .setError(MarketPlaceProto.Error.NONE)
                    .addAllSuccessOrders(successOrders)
                    .putAllFailedItems(failedItems)
                    .addAllActionRequiredOrders(actionRequiredOrders)
                    .build();
        } catch (Exception e) {
            log.error("User {} triggered internal service error with request {}", userId, request, e);
            return MarketPlaceProto.CheckOutResult.newBuilder()
                    .setError(MarketPlaceProto.Error.INTERNAL_SERVICE_ERROR)
                    .build();
        }
    }

    private boolean isGetOrderRequestValid(final MarketPlaceProto.GetAllOrdersRequest request) {
        return request.getMaxCount() > 0;
    }


    @VisibleForTesting
    protected void rollbackOrder(Map<Integer, Integer> itemIdsToQuantity) {
        itemIdsToQuantity.keySet().forEach(a -> itemIdsToQuantity.put(a, -1 * itemIdsToQuantity.get(a)));
        List<Integer> updatedFailedItemInRollback = itemDao.updateItemStock(itemIdsToQuantity);
        if (!updatedFailedItemInRollback.isEmpty()) {
            log.error("Failed to rollback for items {} with map {}", updatedFailedItemInRollback,
                    itemIdsToQuantity);
        }
    }

    private TransactionStatus getPaymentStatus(String transactionId) {
        for (int i = 0; i < CHECKOUT_PAYMENT_MAX_ATTEMPTS; i++) {
            TransactionStatus paymentStatus = transactionServiceHandler.getTransactionStatus(transactionId);
            if (!paymentStatus.equals(TransactionStatus.PENDING)){
                return paymentStatus;
            }
            try{ Thread.sleep(CHECKOUT_PAYMENT_PERIOD); }
            catch (Exception ignored) {}
        }
        return TransactionStatus.PENDING;
    }

    private static boolean isCheckOutRequestValid(MarketPlaceProto.CheckOutRequest request) {
        if (request.getItemsMap() == null) {
            return false;
        }
        // No negative or zero amounts allowed.
        for (Integer quantity : request.getItemsMap().values()) {
            if (quantity <= 0) {
                return false;
            }
        }
        return true;
    }

    // Move invalid items out of loadedItems and into the failedItems map with the given cause.
    @VisibleForTesting
    protected static void moveAllInvalidItems(@NonNull final Collection<Integer> failedItemIdsFromBatch,
                                            @NonNull final Map<Integer, MarketPlaceProto.FailedCheckOutCause> failedItems,
                                            @NonNull final List<Item> loadedItems,
                                            MarketPlaceProto.FailedCheckOutCause cause) {
        failedItemIdsFromBatch.forEach(a -> failedItems.put(a, cause));
        loadedItems.removeIf(a -> failedItemIdsFromBatch.contains(a.getItemID()));
    }

    @VisibleForTesting
    protected static Order generateOrderFromCheckOutItems(@NonNull final String userId,
                                                        @NonNull final Organization organization,
                                                        @NonNull final Collection<Item> items,
                                                        @NonNull final Map<Integer, Integer> orderItems) {
        final String currentDate = TimeUtils.formatDate(new Date());
        final double totalCost = calculateTotalCost(items, orderItems);
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
                .createdAt(TimeUtils.parseDate(currentDate))
                .lastUpdatedAt(TimeUtils.parseDate(currentDate))
                .build();
    }

    @VisibleForTesting
    protected static double calculateTotalCost(Collection<Item> items, Map<Integer, Integer> orderItems) {
        double total = 0.0;
        for (Item item : items) {
            total += item.getItemPrice() * orderItems.get(item.getItemID());
        }
        return total;
    }

    @VisibleForTesting
    protected static Map<String, List<Item>> mapOrgIdToItems(final List<Item> items) {
        final Map<String, List<Item>> result = new HashMap<>();
        for (Item item : items) {
            final List<Item> orgCheckOutItems = result.computeIfAbsent(item.getBelongTo(), k -> new ArrayList<>());
            orgCheckOutItems.add(item);
        }
        return result;
    }

    private static String generateUniqueOrderId() {
        // TODO: Avoid collision with a better generator.
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
                .setLastUpdatedAt(TimeUtils.formatDate(order.getLastUpdatedAt()))
                .setCreatedAt(TimeUtils.formatDate(order.getCreatedAt()))
                .build();
    }

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
                .setItemId(item.getItemID())
                .setCategory(item.getItemCategory())
                .setItemImageLink(item.getItemImageLink())
                .build();
    }
}
