package io.openmarket.marketplace.service;


import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.google.common.collect.ImmutableList;
import io.openmarket.marketplace.MarketPlaceServiceHandler;
import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.dao.ItemDaoImpl;
import io.openmarket.marketplace.grpc.MarketPlaceProto;
import io.openmarket.marketplace.model.Item;
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.service.TransactionServiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static io.openmarket.config.MerchandiseConfig.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class MarketPlaceHandlerTest {
    protected ItemDao itemDao;
    protected MarketPlaceServiceHandler marketPlaceServiceHandler;
    protected TransactionServiceHandler transactionServiceHandler;

    private final String ITEM_NAME = "Mac book proooo";
    private final String CURRENCY_ID = "666";
    private final String ORG_NAME = "ChaCha";
    private final String MY_ID = "miska2";
    private final String ITEMID = "xxx";
    private final String ITEMID2 = "yyyy";
    private final String transactionID = "ddd";
    private final String VALID_CATEGORY = "Foot Wear";
    @BeforeEach
    public void setup() {
        this.itemDao = mock(ItemDaoImpl.class);
        this.transactionServiceHandler = mock(TransactionServiceHandler.class);
        this.marketPlaceServiceHandler = new MarketPlaceServiceHandler(itemDao, transactionServiceHandler);
    }

    @Test
    public void can_get_similar_item_when_in_stock() {
        when(this.itemDao.load(any())).thenReturn(Optional.of(getItem(ITEMID)));
        when(this.itemDao.getItemIdByCategory(VALID_CATEGORY, 3)).thenReturn(ImmutableList.of(ITEMID, ITEMID));
        when(this.itemDao.batchLoad(ImmutableList.of(ITEMID, ITEMID))).thenReturn(ImmutableList.of(getItem(ITEMID), getItem(ITEMID)));
        MarketPlaceProto.GetSimilarItemsResult result = this.marketPlaceServiceHandler.getSimilarItem(getValidSimilarRequest());
        assertEquals(2, result.getItemsCount());
        assertEquals(ITEMID, result.getItems(0).getItemId());
        assertEquals(ITEMID, result.getItems(1).getItemId());
    }

    @Test
    public void can_checkout_when_item_in_stock() {
        when(this.itemDao.load(any())).thenReturn(Optional.of(getItem(ITEMID)));
        when(transactionServiceHandler.createPayment(eq(MY_ID), any())).thenReturn(transactionID);
        when(transactionServiceHandler.getTransactionStatus(transactionID)).thenReturn(TransactionStatus.COMPLETED);
        MarketPlaceProto.CheckOutResult result = this.marketPlaceServiceHandler.checkout(MY_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                .addAllItems(getValidItemsGrpc())
                .setFromOrg(ORG_NAME).setCurrencyId(CURRENCY_ID).build());

        assertEquals(0, result.getUnprocessedItemCount());
        assertEquals(MarketPlaceProto.CheckOutResult.Status.SUCCESS, result.getCheckoutStatus());
    }

    @Test
    public void cannot_checkout_when_item_dont_exist() {
        when(this.itemDao.load(any())).thenReturn(Optional.empty());
        when(transactionServiceHandler.createPayment(eq(MY_ID), any())).thenReturn(transactionID);
        when(transactionServiceHandler.getTransactionStatus(transactionID)).thenReturn(TransactionStatus.COMPLETED);
        MarketPlaceProto.CheckOutResult result = this.marketPlaceServiceHandler.checkout(MY_ID, getValidCheckoutRequest());

        assertEquals(getValidItemsGrpc().size(), result.getUnprocessedItemCount());
        assertEquals(MarketPlaceProto.CheckOutResult.Status.FAIL_ITEM_OUT_OF_STOCK, result.getCheckoutStatus());
    }

    @Test
    public void cannot_checkout_when_item_out_of_stock() {
        when(this.itemDao.load(any())).thenReturn(Optional.of(getItem(ITEMID)));
        doThrow(new ConditionalCheckFailedException("")).when(this.itemDao).update(any());
        when(transactionServiceHandler.createPayment(eq(MY_ID), any())).thenReturn(transactionID);
        when(transactionServiceHandler.getTransactionStatus(transactionID)).thenReturn(TransactionStatus.COMPLETED);
        MarketPlaceProto.CheckOutResult result = this.marketPlaceServiceHandler.checkout(MY_ID, getValidCheckoutRequest());

        assertEquals(getValidItemsGrpc().size(), result.getUnprocessedItemCount());
        assertEquals(MarketPlaceProto.CheckOutResult.Status.FAIL_ITEM_OUT_OF_STOCK, result.getCheckoutStatus());
    }

    @Test
    public void can_partial_checkout_when_some_items_out() {
        when(this.itemDao.load(any())).thenReturn(Optional.of(getItem(ITEMID)));
        doThrow(ConditionalCheckFailedException.class).when(itemDao).update(argThat((UpdateItemRequest r) ->{
            return r.getKey().get(ITEM_DDB_ATTRIBUTE_ID).getS().equals(ITEMID);
        }));
        when(transactionServiceHandler.createPayment(eq(MY_ID), any())).thenReturn(transactionID);
        when(transactionServiceHandler.getTransactionStatus(transactionID)).thenReturn(TransactionStatus.COMPLETED);
        MarketPlaceProto.CheckOutResult result = this.marketPlaceServiceHandler.checkout(MY_ID, getPartialValidCheckoutRequest());

        assertEquals(1, result.getUnprocessedItemCount());
        assertEquals(MarketPlaceProto.CheckOutResult.Status.SUCESS_WITH_FEW_ITEM_OUT_OF_STOCK, result.getCheckoutStatus());

    }

    @Test
    public void can_partial_checkout_when_no_money() {
        when(this.itemDao.load(any())).thenReturn(Optional.of(getItem(ITEMID)));
        when(transactionServiceHandler.createPayment(eq(MY_ID), any())).thenReturn(transactionID);
        when(transactionServiceHandler.getTransactionStatus(transactionID)).thenReturn(TransactionStatus.ERROR);
        MarketPlaceProto.CheckOutResult result = this.marketPlaceServiceHandler.checkout(MY_ID, getValidCheckoutRequest());

        assertEquals(1, result.getUnprocessedItemCount());
        assertEquals(MarketPlaceProto.CheckOutResult.Status.FAIL_TRANSACTION_TIME_OUT, result.getCheckoutStatus());

    }



    private MarketPlaceProto.CheckOutRequest getValidCheckoutRequest() {
        return MarketPlaceProto.CheckOutRequest.newBuilder()
                .addAllItems(getValidItemsGrpc())
                .setFromOrg(ORG_NAME).setCurrencyId(CURRENCY_ID).build();
    }

    private MarketPlaceProto.GetSimilarItemsRequest getValidSimilarRequest() {
        return MarketPlaceProto.GetSimilarItemsRequest.newBuilder()
                .setItemCategory(VALID_CATEGORY)
                .setItemIds(ITEMID2)
                .setWithCount(2).build();
    }

    private MarketPlaceProto.CheckOutRequest getPartialValidCheckoutRequest() {
        return MarketPlaceProto.CheckOutRequest.newBuilder()
                .addAllItems(getSomeOutItemGrpc())
                .setFromOrg(ORG_NAME).setCurrencyId(CURRENCY_ID).build();
    }

    private List<MarketPlaceProto.ItemGrpc> getSomeOutItemGrpc() {
        return ImmutableList.of(
                MarketPlaceProto.ItemGrpc.newBuilder()
                        .setItemId(ITEMID2)
                        .setItemName(ITEM_NAME)
                        .setItemStock(1000)
                        .setItemCount(1)
                        .setItemPrice(10).build(),
                MarketPlaceProto.ItemGrpc.newBuilder()
                        .setItemId(ITEMID)
                        .setItemName(ITEM_NAME)
                        .setItemStock(3)
                        .setItemCount(1)
                        .setItemPrice(10).build()
        );
    }

    private List<MarketPlaceProto.ItemGrpc> getValidItemsGrpc() {
        return ImmutableList.of(
                MarketPlaceProto.ItemGrpc.newBuilder()
                        .setItemCount(1)
                        .setItemId(ITEMID)
                        .setItemName(ITEM_NAME)
                        .setCategory(VALID_CATEGORY)
                        .setItemPrice(10).build()
        );
    }

    private List<Integer> getSomeOutCount() {
        return ImmutableList.of(1, 10);
    }

    private Item getItem(String itemid) {
        return Item.builder().stock(99).itemID(itemid)
                .itemName(ITEM_NAME).itemImageLink("xxx")
                .belongTo(ORG_NAME).itemDescription("xxx")
                .itemCategory(VALID_CATEGORY)
                .itemPrice(50.0)
                .build();
    }
}

