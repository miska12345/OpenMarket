package io.openmarket.marketplace;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.google.common.collect.ImmutableMap;
import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.grpc.MarketPlaceProto.*;
import io.openmarket.marketplace.model.Item;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.service.TransactionServiceHandler;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.sql.SQLException;
import java.util.*;

import static io.openmarket.config.MerchandiseConfig.*;

@Log4j2
public class MarketPlaceServiceHandler {
    private final ItemDao itemDao;
    private final TransactionServiceHandler transactionServiceHandler;

    //the checkout is aborted after checking transaction status for 20 second
    private final int CHECKOUT_TIMEOUT = 10;
    private final int CHECKOUT_QUERY_DELAY = 2000;
    @Inject
    public MarketPlaceServiceHandler(@Nonnull final ItemDao itemDao,
                                     @Nonnull final TransactionServiceHandler transactionServiceHandler) {
        this.itemDao = itemDao;
        this.transactionServiceHandler = transactionServiceHandler;
    }

    public GetOrgItemsResult getListingByOrgId(GetOrgItemsRequest request) throws SQLException {
        List<Integer> itemIds = this.itemDao.getItemIdsByOrg(request.getOrgId());
        List<Item> selling = this.itemDao.batchLoad(itemIds);
        List<ItemGrpc> result = new ArrayList<>();

        for(Item item : selling) {
            result.add(convertToGrpc(item));
        }

        return GetOrgItemsResult.newBuilder().addAllItems(result).build();
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

    private boolean validateItemGrpc(ItemGrpc target) {
        if (target.getItemCount() < 1 || target.getItemName().isEmpty() || target.getCategory().isEmpty()
        || target.getItemCount() < 1 || target.getItemPrice() < 0 || target.getBelongTo().isEmpty()
        || target.getItemImageLink().isEmpty() || target.getItemStock() < 1){
            return false;
        }

        return true;
    }
    private ItemGrpc convertToGrpc(Item item) {
        return ItemGrpc.newBuilder()
                .setItemName(item.getItemName()).setItemStock(item.getStock())
                .setBelongTo(item.getBelongTo())
                .setItemPrice(item.getItemPrice())
                .setItemDescription(item.getItemDescription())
                .setItemId(item.getItemID())
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
//
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
