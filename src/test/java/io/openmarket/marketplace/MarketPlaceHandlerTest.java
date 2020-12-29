package io.openmarket.marketplace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.openmarket.marketplace.dao.ItemDao;
import io.openmarket.marketplace.grpc.MarketPlaceProto;
import io.openmarket.marketplace.model.Item;
import io.openmarket.order.dao.OrderDao;
import io.openmarket.order.model.ItemInfo;
import io.openmarket.order.model.Order;
import io.openmarket.order.model.OrderStatus;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.model.Organization;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionErrorType;
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.model.TransactionType;
import io.openmarket.transaction.service.TransactionServiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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

    private static final Order ORDER_A = Order.builder()
            .orderId("abc")
            .buyerId(BUYER_ID)
            .sellerId(SELLER_ID)
            .currency("DashCoin")
            .items(ImmutableList.of())
            .transactionId("123")
            .status(OrderStatus.PAYMENT_CONFIRMED)
            .lastUpdatedAt(new Date())
            .createdAt(new Date())
            .build();

    private ItemDao itemDao;
    private OrderDao orderDao;
    private OrgServiceHandler orgServiceHandler;
    private MarketPlaceServiceHandler marketPlaceServiceHandler;
    private TransactionServiceHandler transactionServiceHandler;
    private TransactionServiceHandler.Stepper stepper;
    TransactionServiceHandler.Stepper mockStepper;

    @BeforeEach
    public void setup() {
        this.itemDao = mock(ItemDao.class);
        this.orderDao = mock(OrderDao.class);
        this.orgServiceHandler = mock(OrgServiceHandler.class);
        this.transactionServiceHandler = mock(TransactionServiceHandler.class);
        this.marketPlaceServiceHandler = new MarketPlaceServiceHandler(itemDao, orderDao, orgServiceHandler,
                transactionServiceHandler);
        this.stepper = mock(TransactionServiceHandler.Stepper.class);

        mockStepper = mock(TransactionServiceHandler.Stepper.class);
        when(stepper.getTransaction()).thenReturn(Transaction.builder()
                .transactionId(TRANSACTION_ID)
                .currencyId("DashCoin")
                .type(TransactionType.PAY)
                .amount(1.0)
                .payerId(BUYER_ID)
                .status(TransactionStatus.COMPLETED)
                .recipientId(SELLER_ID)
                .error(TransactionErrorType.NONE)
                .createdAt(new Date())
                .lastUpdatedAt(new Date())
                .build());
    }

    @Test
    public void testMoveAllInvalidItems() {
        List<Integer> failedItemIds = Arrays.asList(1, 2);
        Map<Integer, MarketPlaceProto.FailedCheckOutCause> causeMap = new HashMap<>();
        List<Item> items = new ArrayList<>(Arrays.asList(ORG_A_ITEM_IN_STOCK, ORG_A_ITEM_IN_STOCK_2));
        MarketPlaceServiceHandler.moveAllInvalidItems(failedItemIds, causeMap, items, MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK);
        assertTrue(items.isEmpty());
        assertEquals(2, causeMap.size());
        for (Map.Entry<Integer, MarketPlaceProto.FailedCheckOutCause> entry : causeMap.entrySet()) {
            assertEquals(MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK, entry.getValue());
        }
    }

    @Test
    public void testMapOrgToItems() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_B_ITEM_IN_STOCK);
        Map<String, List<Item>> result = MarketPlaceServiceHandler.mapOrgIdToItems(items);
        assertEquals(2, result.size());
    }

    @Test
    public void testCalculateTotalCost() {
        List<Item> items = ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_A_ITEM_IN_STOCK_2);
        Map<Integer, Integer> itemIdToQuantity = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1, ORG_A_ITEM_IN_STOCK_2.getItemID(), 1);
        assertEquals(ORG_A_ITEM_IN_STOCK.getItemPrice() + ORG_A_ITEM_IN_STOCK_2.getItemPrice(),
                MarketPlaceServiceHandler.calculateTotalCost(items, itemIdToQuantity));
    }

    @Test
    public void testRollback() {
        Map<Integer, Integer> rollbackMap = new HashMap<>();
        rollbackMap.put(1, 1);
        marketPlaceServiceHandler.rollbackOrder(rollbackMap);
        verify(itemDao, times(1)).updateItemStock(argThat(a -> a.get(1) < 0));
    }

    @Test
    public void testGetAllOrdersAsBuyer() {
        when(orderDao.batchLoad(anyCollection())).thenAnswer(a -> {
           List<Order> orders = new ArrayList<>();
           orders.add(ORDER_A);
           return orders;
        });
        when(orderDao.getOrderByBuyer(anyString(), anyMap(), anyCollection(), anyInt())).thenAnswer(a -> {
           Collection<String> ids = a.getArgument(2);
           System.out.println(ids);
           ids.add(ORDER_A.getOrderId());
           return null;
        });
        MarketPlaceProto.GetAllOrdersResult result = marketPlaceServiceHandler
                .getOrders(BUYER_ID, MarketPlaceProto.GetAllOrdersRequest.newBuilder()
                .setRole(MarketPlaceProto.Role.BUYER)
                .setMaxCount(1)
                .build()
        );
        verify(orderDao, times(1)).getOrderByBuyer(eq(BUYER_ID), any(), any(), eq(1));
        assertEquals(1, result.getOrdersCount());
    }

    @Test
    public void testGetAllOrdersAsSeller() {
        when(orderDao.batchLoad(anyCollection())).thenAnswer(a -> {
            List<Order> orders = new ArrayList<>();
            orders.add(ORDER_A);
            return orders;
        });
        when(orderDao.getOrderByBuyer(anyString(), anyMap(), anyCollection(), anyInt())).thenAnswer(a -> {
            Collection<String> ids = a.getArgument(2);
            ids.add(ORDER_A.getOrderId());
            return null;
        });
        MarketPlaceProto.GetAllOrdersResult result = marketPlaceServiceHandler
                .getOrders(SELLER_ID, MarketPlaceProto.GetAllOrdersRequest.newBuilder()
                        .setRole(MarketPlaceProto.Role.SELLER)
                        .setMaxCount(1)
                        .build()
                );
        verify(orderDao, times(1)).getOrderBySeller(eq(SELLER_ID), any(), any(), eq(1));
        assertEquals(1, result.getOrdersCount());
    }

    @Test
    public void testGetAllOrders_Illegal_Request() {
        assertThrows(IllegalArgumentException.class, () -> marketPlaceServiceHandler
                .getOrders(SELLER_ID, MarketPlaceProto.GetAllOrdersRequest.newBuilder()
                        .setRole(MarketPlaceProto.Role.SELLER)
                        .setMaxCount(-1)
                        .build()
                ));
    }

    @Test
    public void test_CheckOut_Single_Item_Success() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
           return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK));
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(itemDao.updateItemStock(anyMap())).thenReturn(new ArrayList<>());

        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                .putAllItems(cart)
        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(1, 0, 0, result);
//        verify(orderDao, times(1)).save(argThat(a -> a.getStatus()
//                .equals(OrderStatus.PAYMENT_CONFIRMED) && checkOrderIsCorrect(cart, a)));
    }

    @Test
    public void test_CheckOut_Single_Item_Out_of_Stock() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_OUT_OF_STOCK.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_OUT_OF_STOCK));
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(itemDao.updateItemStock(anyMap())).thenReturn(new ArrayList<>(Collections.singletonList(ORG_A_ITEM_OUT_OF_STOCK.getItemID())));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(0, 1, 0, result);
        verify(orderDao, times(0)).save(any());
        verify(transactionServiceHandler, times(0)).createPayment(anyString(), any());
        assertEquals(MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK,
                result.getFailedItemsMap().get(ORG_A_ITEM_OUT_OF_STOCK.getItemID()));
    }

    @Test
    public void test_CheckOut_Multiple_Items_Same_Org_Success() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(),
                1, ORG_A_ITEM_IN_STOCK_2.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_A_ITEM_IN_STOCK_2));
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(itemDao.updateItemStock(anyMap())).thenReturn(new ArrayList<>());
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(1, 0, 0, result);
//        verify(orderDao, times(1)).save(argThat(a -> a.getStatus()
//                .equals(OrderStatus.PAYMENT_CONFIRMED) && checkOrderIsCorrect(cart, a)));
    }

    @Test
    public void test_CheckOut_Invalid_Item() {
        // Invalid because organizationId does not exist.
        Map<Integer, Integer> cart = ImmutableMap.of(INVALID_ITEM.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(INVALID_ITEM));
        });
        when(orgServiceHandler.getOrgByName(INVALID_ITEM.getBelongTo())).thenReturn(Optional.empty());
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(0, 1, 0, result);
        verify(orderDao, times(0)).save(any());
        verify(transactionServiceHandler, times(0)).createPayment(anyString(), any());
        assertEquals(MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST, result.getFailedItemsMap().get(INVALID_ITEM.getItemID()));
    }

    @Test
    public void test_CheckOut_Invalid_Item_2() {
        // Invalid because itemId does not exist.
        Map<Integer, Integer> cart = ImmutableMap.of(INVALID_ITEM_2.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            Collection<Integer> failedItemID = a.getArgument(1);
            failedItemID.addAll(a.getArgument(0));
            return Collections.emptyList();
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(0, 1, 0, result);
        verify(orderDao, times(0)).save(any());
        verify(transactionServiceHandler, times(0)).createPayment(anyString(), any());
        verify(itemDao, times(0)).updateItemStock(anyMap());
        assertEquals(MarketPlaceProto.FailedCheckOutCause.ITEM_DOES_NOT_EXIST,
                result.getFailedItemsMap().get(INVALID_ITEM_2.getItemID()));
    }

    @Test
    public void test_Only_CheckOut_In_Stock_Items() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
                ORG_A_ITEM_IN_STOCK_2.getItemID(), 1,
                ORG_A_ITEM_OUT_OF_STOCK.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_A_ITEM_IN_STOCK_2, ORG_A_ITEM_OUT_OF_STOCK));
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(itemDao.updateItemStock(anyMap()))
                .thenReturn(new ArrayList<>(Collections.singletonList(ORG_A_ITEM_OUT_OF_STOCK.getItemID())));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(1, 1, 0, result);
//        verify(orderDao, times(1)).save(argThat(a -> a.getStatus()
//                .equals(OrderStatus.PAYMENT_CONFIRMED) && checkOrderIsCorrect(ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
//                ORG_A_ITEM_IN_STOCK_2.getItemID(), 1), a)));
        assertEquals(MarketPlaceProto.FailedCheckOutCause.OUT_OF_STOCK,
                result.getFailedItemsMap().get(ORG_A_ITEM_OUT_OF_STOCK.getItemID()));
    }

    @Test
    public void test_CheckOut_Multiple_Organization_In_Stock_Items() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
                ORG_B_ITEM_IN_STOCK.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_B_ITEM_IN_STOCK));
        });
        when(transactionServiceHandler.createPaymentStepper(any(), any())).thenReturn(stepper);
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(orgServiceHandler.getOrgByName(ORGANIZATION_B.getOrgName())).thenReturn(Optional.of(ORGANIZATION_B));
        when(itemDao.updateItemStock(anyMap())).thenReturn(new ArrayList<>());
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(2, 0, 0, result);
//        verify(orderDao, times(2)).save(argThat(a -> a.getStatus()
//                .equals(OrderStatus.PAYMENT_CONFIRMED)));
//        verify(transactionServiceHandler, times(2)).createPayment(eq(BUYER_ID), any());
    }

    @Test
    public void test_CheckOut_Multiple_Organizations_Mixed_Items() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1,
                ORG_B_ITEM_IN_STOCK.getItemID(), 1,
                ORG_A_ITEM_OUT_OF_STOCK.getItemID(), 1,
                INVALID_ITEM.getItemID(), 1);
        mockSuccessTransaction();
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_B_ITEM_IN_STOCK, ORG_A_ITEM_OUT_OF_STOCK, INVALID_ITEM));
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        when(orgServiceHandler.getOrgByName(ORGANIZATION_B.getOrgName())).thenReturn(Optional.of(ORGANIZATION_B));
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            Collection<Integer> failedItemID = a.getArgument(1);
            failedItemID.add(INVALID_ITEM.getItemID());
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK, ORG_B_ITEM_IN_STOCK));
        });
        when(itemDao.updateItemStock(any())).thenReturn(new ArrayList<>(ImmutableList.of(ORG_A_ITEM_OUT_OF_STOCK.getItemID())));

        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());

        assertEquals(MarketPlaceProto.Error.NONE, result.getError());
        assertResultCountMatches(2, 2, 0, result);
//        verify(orderDao, times(2)).save(argThat(a -> a.getStatus()
//                .equals(OrderStatus.PAYMENT_CONFIRMED)));
//        verify(transactionServiceHandler, times(2)).createPayment(eq(BUYER_ID), any());
        assertEquals(1, result.getSuccessOrdersList().get(0).getItemsCount());
        assertEquals(1, result.getSuccessOrdersList().get(1).getItemsCount());
        assertEquals(ORG_B_ITEM_IN_STOCK.getItemID(), result.getSuccessOrdersList().get(0).getItemsList().get(0).getItemId());
        assertEquals(ORG_A_ITEM_IN_STOCK.getItemID(), result.getSuccessOrdersList().get(1).getItemsList().get(0).getItemId());
    }

    @Test
    public void test_Internal_Service_Error() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1);
        doThrow(IllegalStateException.class).when(itemDao).batchLoad(any(), anyCollection());
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        assertEquals(MarketPlaceProto.Error.INTERNAL_SERVICE_ERROR, result.getError());
        verify(orderDao, times(0)).save(any());
        verify(transactionServiceHandler, times(0)).createPayment(anyString(), any());
        verify(itemDao, times(0)).updateItemStock(any());
    }

    @Test
    public void test_Insufficient_Balance_Before_Order() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1);
        when(transactionServiceHandler.getBalanceForCurrency(BUYER_ID, ORG_A_CURRENCY)).thenReturn(0.0);
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK));
        });
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        assertResultCountMatches(0, 1, 0, result);
        verify(orderDao, times(0)).save(any());
        verify(transactionServiceHandler, times(0)).createPayment(anyString(), any());
        verify(itemDao, times(0)).updateItemStock(any());
        assertEquals(MarketPlaceProto.FailedCheckOutCause.INSUFFICIENT_BALANCE,
                result.getFailedItemsMap().get(ORG_A_ITEM_IN_STOCK.getItemID()));
    }

    @Test
    public void test_Insufficient_Balance_After_Order() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1);
        when(transactionServiceHandler.createPaymentStepper(eq(BUYER_ID), any())).thenReturn(this.stepper);
        when(transactionServiceHandler.getBalanceForCurrency(BUYER_ID, ORG_A_CURRENCY)).thenReturn(9999.0);
        when(transactionServiceHandler.createPayment(eq(BUYER_ID), any())).thenReturn(TRANSACTION_ID);
        when(transactionServiceHandler.getTransactionStatus(anyString())).thenReturn(TransactionStatus.ERROR);
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK));
        });
        when(itemDao.updateItemStock(any())).thenReturn(new ArrayList<>());
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        assertResultCountMatches(0, 1, 0, result);
//        verify(orderDao, times(0)).save(any());
//        verify(itemDao, times(2)).updateItemStock(any());
        assertEquals(MarketPlaceProto.FailedCheckOutCause.INSUFFICIENT_BALANCE,
                result.getFailedItemsMap().get(ORG_A_ITEM_IN_STOCK.getItemID()));
    }

    @Test
    public void test_Unknown_Payment_Status() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), 1);
        when(transactionServiceHandler.getBalanceForCurrency(BUYER_ID, ORG_A_CURRENCY)).thenReturn(9999.0);
        when(transactionServiceHandler.createPaymentStepper(any(), any())).thenReturn(stepper);
//        when(transactionServiceHandler.createPayment(eq(BUYER_ID), any())).thenReturn(TRANSACTION_ID);
        when(transactionServiceHandler.getTransactionStatus(anyString())).thenReturn(TransactionStatus.PENDING);
        when(itemDao.batchLoad(anyCollection(), any())).thenAnswer(a -> {
            return new ArrayList<>(ImmutableList.of(ORG_A_ITEM_IN_STOCK));
        });
        when(itemDao.updateItemStock(any())).thenReturn(new ArrayList<>());
        when(orgServiceHandler.getOrgByName(ORGANIZATION_A.getOrgName())).thenReturn(Optional.of(ORGANIZATION_A));
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        assertResultCountMatches(0, 0, 1, result);
        verify(orderDao, times(1)).save(argThat(a -> a.getStatus().equals(OrderStatus.PENDING_PAYMENT)));
    }

    @Test
    public void test_Negative_CheckOut_Quantity() {
        Map<Integer, Integer> cart = ImmutableMap.of(ORG_A_ITEM_IN_STOCK.getItemID(), -1);
        MarketPlaceProto.CheckOutResult result = marketPlaceServiceHandler
                .checkout(BUYER_ID, MarketPlaceProto.CheckOutRequest.newBuilder()
                        .putAllItems(cart)
                        .build());
        assertEquals(MarketPlaceProto.Error.INTERNAL_SERVICE_ERROR, result.getError());
        assertResultCountMatches(0, 0, 0, result);
    }

    private boolean checkOrderIsCorrect(Map<Integer, Integer> cart, Order order) {
        assertEquals(cart.size(), order.getItems().size());
        assertEquals(calculateCartTotalCost(cart), order.getTotal());
        for (ItemInfo info : order.getItems()) {
            assertTrue(cart.containsKey(info.getItemId()));
            assertEquals(cart.get(info.getItemId()), info.getQuantity());
        }
        return true;
    }

    private void mockSuccessTransaction() {
        when(transactionServiceHandler.createPayment(eq(BUYER_ID), any())).thenReturn(TRANSACTION_ID);
        when(transactionServiceHandler.getTransactionStatus(anyString())).thenReturn(TransactionStatus.COMPLETED);
        when(transactionServiceHandler.getBalanceForCurrency(eq(BUYER_ID), anyString())).thenReturn(Double.MAX_VALUE);
        when(transactionServiceHandler.createPaymentStepper(any(), any())).thenReturn(stepper);
    }

    private static void assertResultCountMatches(int numSuccess, int numFailed, int numUnknown, MarketPlaceProto.CheckOutResult result) {
        assertEquals(numSuccess, result.getSuccessOrdersCount());
        assertEquals(numFailed, result.getFailedItemsCount());
        assertEquals(numUnknown, result.getActionRequiredOrdersCount());
    }

    private double calculateCartTotalCost(Map<Integer, Integer> cart) {
        double total = 0.0;
        for (int itemId : cart.keySet()) {
            total += cart.get(itemId) * ITEM_ID_TO_PRICE_MAP.get(itemId);
        }
        return total;
    }
}

