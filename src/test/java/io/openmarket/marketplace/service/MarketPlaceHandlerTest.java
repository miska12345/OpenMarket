package io.openmarket.marketplace.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.google.common.collect.ImmutableList;
import io.grpc.Context;
import io.openmarket.marketplace.MarketPlaceServiceHandler;
import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.dao.ItemDaoImpl;
import io.openmarket.marketplace.grpc.MarketPlaceProto;
import io.openmarket.marketplace.model.Item;
import io.openmarket.server.config.InterceptorConfig;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
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

    final String ITEM_NAME = "Mac book proooo";
    final String CURRENCY_ID = "666";
    final String ORG_NAME = "ChaCha";
    final String MY_ID = "miska2";
    final String ITEMID = "xxx";
    final String transactionID = "ddd";
    @BeforeEach
    public void setup() {
        this.itemDao = mock(ItemDaoImpl.class);
        this.transactionServiceHandler = mock(TransactionServiceHandler.class);
        this.marketPlaceServiceHandler = new MarketPlaceServiceHandler(itemDao, transactionServiceHandler);
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

    private MarketPlaceProto.CheckOutRequest getPartialValidCheckoutRequest() {
        return MarketPlaceProto.CheckOutRequest.newBuilder()
                .addAllItems(getSomeOutItemGrpc())
                .setFromOrg(ORG_NAME).setCurrencyId(CURRENCY_ID).build();
    }

    private List<MarketPlaceProto.ItemGrpc> getSomeOutItemGrpc() {
        return ImmutableList.of(
                MarketPlaceProto.ItemGrpc.newBuilder()
                        .setItemId("6778366f-78d4-46b7-8560-41aa40cd6b97")
                        .setItemName("Mac book prooo")
                        .setItemStock(1000)
                        .setItemCount(1)
                        .setItemPrice(10).build(),
                MarketPlaceProto.ItemGrpc.newBuilder()
                        .setItemId(ITEMID)
                        .setItemName("Mac book prooo")
                        .setItemStock(3)
                        .setItemCount(1)
                        .setItemPrice(10).build()
        );
    }

    private List<MarketPlaceProto.ItemGrpc> getValidItemsGrpc() {
        return ImmutableList.of(
                MarketPlaceProto.ItemGrpc.newBuilder()
                        .setItemId("6778366f-78d4-46b7-8560-41aa40cd6b97")
                        .setItemName("Mac book prooo")
                        .setItemCount(1)
                        .setItemPrice(10).build()
        );
    }

    private List<Integer> getSomeOutCount() {
        return ImmutableList.of(1, 10);
    }

    private Item getItem(String itemid) {
        return Item.builder().stock(99)
                .itemID(itemid)
                .itemName("didntpay")
                .belongTo("ChaCha")
                .itemCategory("foot waer")
                .itemImageLink("wat")
                .itemDescription("watever")
                .itemPrice(50.0).build();
    }

    private List<Integer> getValidCount() {
        return ImmutableList.of(1);
    }

    private Context getContext() {
        return Context.current().withValue(InterceptorConfig.USER_NAME_CONTEXT_KEY, MY_ID);
    }
}

