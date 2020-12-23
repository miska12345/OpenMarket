package io.openmarket.marketplace.service;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.openmarket.marketplace.MarketPlaceServiceHandler;
import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.grpc.MarketPlaceProto;
import io.openmarket.marketplace.model.Item;
import io.openmarket.order.dao.OrderDao;
import io.openmarket.order.model.ItemInfo;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.model.Organization;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionErrorType;
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.model.TransactionType;
import io.openmarket.transaction.service.TransactionServiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.openmarket.order.model.OrderStatus.PENDING_PAYMENT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MarketPlaceHandlerTest {
    private static final String BUYER_ID = "123";
    private static final String SELLER_ID = "321";
    private static final String TRANSACTION_ID = "abcdefg";

    private static final String ORG_A_CURRENCY = "DashCoin";
    private static final String ORG_B_CURRENCY = "Silicoin";
    private static final Organization ORGANIZATION_A = Organization.builder()
            .orgName("A+")
            .orgCurrency(ORG_A_CURRENCY)
            .orgOwnerId(SELLER_ID)
            .orgDescription("")
            .orgPosterS3Key("")
            .orgPortraitS3Key("")
            .build();

    private static final Organization ORGANIZATION_B = Organization.builder()
            .orgName("B+")
            .orgCurrency(ORG_B_CURRENCY)
            .orgOwnerId(SELLER_ID)
            .orgDescription("")
            .orgPosterS3Key("")
            .orgPortraitS3Key("")
            .build();

    private static final Item ORG_A_ITEM_IN_STOCK = Item.builder().itemID(1).itemName("Cup")
            .belongTo(ORGANIZATION_A.getOrgName()).itemPrice(10.0).stock(10).build();
    private static final Item ORG_A_ITEM_IN_STOCK_2 = Item.builder().itemID(2).itemName("Brush")
            .belongTo(ORGANIZATION_A.getOrgName()).itemPrice(10.0).stock(1).build();

    private static final Item ORG_A_ITEM_OUT_OF_STOCK = Item.builder().itemID(3).itemName("Phone")
            .belongTo(ORGANIZATION_A.getOrgName()).itemPrice(10.0).stock(0).build();

    private static final Item ORG_B_ITEM_IN_STOCK = Item.builder().itemID(4).itemName("bPhone")
            .belongTo(ORGANIZATION_B.getOrgName()).itemPrice(10.0).stock(10).build();

    private static final Item INVALID_ITEM = Item.builder().itemID(5).itemName("XXX")
            .belongTo("InvalidOrganization").itemPrice(10.0).stock(100).build();

    private static final Item INVALID_ITEM_2 = Item.builder().itemID(5).itemName("YYY")
            .belongTo(ORGANIZATION_A.getOrgName()).itemPrice(10.0).stock(100).build();

    private static final Map<Integer, Double> ITEM_ID_TO_PRICE_MAP = ImmutableMap.of(
            ORG_A_ITEM_IN_STOCK.getItemID(), ORG_A_ITEM_IN_STOCK.getItemPrice(),
            ORG_A_ITEM_IN_STOCK_2.getItemID(), ORG_A_ITEM_IN_STOCK_2.getItemPrice(),
            ORG_A_ITEM_OUT_OF_STOCK.getItemID(), ORG_A_ITEM_OUT_OF_STOCK.getItemPrice()
            );

    private ItemDao itemDao;
    private OrderDao orderDao;
    private OrgServiceHandler orgServiceHandler;
    private MarketPlaceServiceHandler marketPlaceServiceHandler;
    private TransactionServiceHandler transactionServiceHandler;
    TransactionServiceHandler.Stepper mockStepper;

    @BeforeEach
    public void setup() {
        this.itemDao = mock(ItemDao.class);
        this.orderDao = mock(OrderDao.class);
        this.orgServiceHandler = mock(OrgServiceHandler.class);
        this.transactionServiceHandler = mock(TransactionServiceHandler.class);
        this.marketPlaceServiceHandler = new MarketPlaceServiceHandler(itemDao, orderDao, orgServiceHandler,
                transactionServiceHandler);
        mockStepper = mock(TransactionServiceHandler.Stepper.class);
    }

    @Test
    public void test_CheckOut_Single_Item_Success() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK);
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1);
        createValidTransactionStepper();
        createValidSetUpForItems(items);

        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                .putAllItems(cart)
        .build());

        verify(mockStepper, times(1)).commit();
        verify(orderDao, times(1)).save(argThat(a -> {
            return a.getItems().size() == 1 && isItemInfoMatchItem(a.getItems().get(0), ORG_A_ITEM_IN_STOCK);
        }));
        assertOrderDBEntry(1, calculateCartTotalCost(cart));
        assertResultCountMatches(1, 0, result);
        assertOrderContainsItem(result.getSuccessOrdersList(), items);
    }

    @Test
    public void test_CheckOut_Single_Item_Out_of_Stock() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_OUT_OF_STOCK.getItemID(), 1);
        createValidSetUpForItems(ImmutableList.of(ORG_A_ITEM_OUT_OF_STOCK));
        MarketPlaceProto.CheckOutResult result =  marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                .putAllItems(cart)
                .build());

        verify(mockStepper, times(0)).commit();
        verify(orderDao, times(0)).save(any());
        assertResultCountMatches(0, 1, result);
        assertEquals(MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK,
                result.getFailedItemsMap().get(ORG_A_ITEM_OUT_OF_STOCK.getItemID()));
    }

    @Test
    public void test_CheckOut_Multiple_Items_Success() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_A_ITEM_IN_STOCK_2);
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(),
                1, ORG_A_ITEM_IN_STOCK_2.getItemID(), 1);
        createValidTransactionStepper();
        createValidSetUpForItems(items);

        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        verify(mockStepper, times(1)).commit();
        assertResultCountMatches(1, 0, result);
        assertOrderDBEntry(cart.size(), calculateCartTotalCost(cart));
        assertOrderContainsItem(result.getSuccessOrdersList(), items);
    }

    @Test
    public void test_CheckOut_Invalid_Item() {
        // Invalid because organizationId does not exist.
        List<Item> items = ImmutableList.of(INVALID_ITEM);
        Map<Integer, Integer> cart = ImmutableMap.of(INVALID_ITEM.getItemID(), 1);
        when(orgServiceHandler.getOrg(anyString())).thenReturn(Optional.empty());
        createValidTransactionStepper();
        when(itemDao.batchLoad(any(), any())).thenReturn(ImmutableList.of(INVALID_ITEM));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        verify(mockStepper, times(0)).commit();
        assertNoOrderDbEntry();
        assertResultCountMatches(0, 1, result);
    }

    @Test
    public void test_CheckOut_Invalid_Item_2() {
        // Invalid because itemId does not exist.
        Map<Integer, Integer> cart = ImmutableMap.of(INVALID_ITEM_2.getItemID(), 1);
        when(orgServiceHandler.getOrg(anyString())).thenReturn(Optional.of(ORGANIZATION_A));
        createValidTransactionStepper();
        when(itemDao.batchLoad(any(), any())).thenAnswer((a)-> {
           Collection<Integer> failedIds = a.getArgument(1);
           failedIds.add(INVALID_ITEM_2.getItemID());
           return Collections.emptyList();
        });
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        verify(mockStepper, times(0)).commit();
        assertNoOrderDbEntry();
        assertResultCountMatches(0, 1, result);
        assertEquals(MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST,
                result.getFailedItemsMap().get(INVALID_ITEM_2.getItemID()));
    }

    @Test
    public void test_Only_CheckOut_In_Stock_Items() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_A_ITEM_IN_STOCK_2, ORG_A_ITEM_OUT_OF_STOCK);
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
                ORG_A_ITEM_IN_STOCK_2.getItemID(), 1,
                ORG_A_ITEM_OUT_OF_STOCK.getItemID(), 1);
        createValidTransactionStepper();
        createValidSetUpForItems(items);
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        verify(mockStepper, times(1)).commit();
        assertOrderDBEntry(2, ORG_A_ITEM_IN_STOCK.getItemPrice() + ORG_A_ITEM_IN_STOCK_2.getItemPrice());
        assertResultCountMatches(1, 1, result);
        assertEquals(MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK,
                result.getFailedItemsMap().get(ORG_A_ITEM_OUT_OF_STOCK.getItemID()));
    }

    @Test
    public void test_CheckOut_Multiple_Organization_In_Stock_Items() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_B_ITEM_IN_STOCK);
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
                ORG_B_ITEM_IN_STOCK.getItemID(), 1);
        createValidTransactionStepper();
        when(itemDao.batchLoad(anyCollection(), anyCollection())).thenReturn(items);
        when(orgServiceHandler.getOrg(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(orgServiceHandler.getOrg(ORGANIZATION_B.getOrgName())).thenReturn(Optional.of(ORGANIZATION_B));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        verify(mockStepper, times(2)).commit();
        verify(mockStepper, times(2)).commit();
        assertResultCountMatches(2, 0, result);
        verify(orderDao, times(2)).save(any());
    }

    @Test
    public void test_CheckOut_Multiple_Organizations_Mixed_Items() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_B_ITEM_IN_STOCK, ORG_A_ITEM_OUT_OF_STOCK, INVALID_ITEM);
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
                ORG_B_ITEM_IN_STOCK.getItemID(), 1,
                ORG_A_ITEM_OUT_OF_STOCK.getItemID(), 1,
                INVALID_ITEM.getItemID(), 1);
        createValidTransactionStepper();
        when(itemDao.batchLoad(anyCollection(), anyCollection())).thenReturn(items);
        when(orgServiceHandler.getOrg(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(orgServiceHandler.getOrg(ORGANIZATION_B.getOrgName())).thenReturn(Optional.of(ORGANIZATION_B));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        verify(mockStepper, times(2)).commit();
        verify(orderDao, times(2)).save(any());
        assertResultCountMatches(2, 2, result);
    }

    private void assertNoOrderDbEntry() {
        verify(orderDao, times(0)).save(any());
    }

    private void assertOrderContainsItem(List<MarketPlaceProto.Order> successOrders, List<Item> itemsSold) {
        List<Integer> itemIds = itemsSold.stream().map(Item::getItemID).collect(Collectors.toList());
        for (MarketPlaceProto.Order order : successOrders) {
            for (MarketPlaceProto.OrderedItem info : order.getItemsList()) {
                assertTrue(itemIds.contains(info.getItemId()));
            }
        }
    }

    private void assertOrderDBEntry(int numItems, double total) {
        verify(orderDao, times(1)).save(argThat(a -> {
            return a.getItems().size() == numItems
                    && a.getTransactionId() != null
                    && a.getStatus().equals(PENDING_PAYMENT)
                    && a.getTotal() == total;
        }));
    }

    private static void assertResultCountMatches(int numSuccess, int numFailed, MarketPlaceProto.CheckOutResult result) {
        assertEquals(numSuccess, result.getSuccessOrdersCount());
        assertEquals(numFailed, result.getFailedItemsCount());
    }

    private void createValidTransactionStepper() {
        when(transactionServiceHandler.createPaymentStepper(anyString(), any())).thenAnswer(a -> {
            when(mockStepper.getTransaction()).thenReturn(generateTransaction(TRANSACTION_ID,
                    ((TransactionProto.PaymentRequest)a.getArgument(1)).getMoneyAmount().getCurrencyId(),
                    ((TransactionProto.PaymentRequest)a.getArgument(1)).getMoneyAmount().getAmount()));
            return mockStepper;
        });
    }

    private void createValidSetUpForItems(List<Item> item) {
        when(itemDao.batchLoad(anyCollection(), anyCollection())).thenReturn(item);
        when(orgServiceHandler.getOrg(anyString())).thenReturn(Optional.of(ORGANIZATION_A));
    }

    private double calculateCartTotalCost(Map<Integer, Integer> cart) {
        double total = 0.0;
        for (int itemId : cart.keySet()) {
            total += cart.get(itemId) * ITEM_ID_TO_PRICE_MAP.get(itemId);
        }
        return total;
    }

    private static boolean isItemInfoMatchItem(ItemInfo itemInfo, Item item) {
        return itemInfo.getItemId() == item.getItemID() && itemInfo.getItemName().equals(item.getItemName());
    }

    private static Transaction generateTransaction(String transactionId, String currency, double amount) {
        return Transaction.builder()
                .transactionId(transactionId)
                .recipientId(SELLER_ID)
                .payerId(BUYER_ID)
                .status(TransactionStatus.PENDING)
                .amount(amount)
                .type(TransactionType.PAY)
                .currencyId(currency)
                .error(TransactionErrorType.NONE)
                .build();
    }
}

