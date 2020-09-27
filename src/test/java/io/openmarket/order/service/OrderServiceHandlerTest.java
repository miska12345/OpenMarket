package io.openmarket.order.service;

import com.google.common.collect.ImmutableList;
import io.openmarket.order.OrderServiceHandler;
import io.openmarket.order.dao.OrderDao;
import io.openmarket.order.grpc.OrderOuterClass.*;
import io.openmarket.order.model.ItemInfo;
import io.openmarket.order.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;


public class OrderServiceHandlerTest {


    private OrderDao orderDao;
    private OrderServiceHandler handler;

    @BeforeEach
    public void setup() {
        this.orderDao = mock(OrderDao.class);
        this.handler = new OrderServiceHandler(orderDao);
    }

    @Test
    public void when_Query_By_ID_And_Exists_Then_Return() {
        Order order1 = generateOrder("abcdefg");
        Order order2 = generateOrder("abcdefgh");
        orderDao.save(order1);
        orderDao.save(order2);
        getOrdersByUserIdRequest request = getOrdersByUserIdRequest.newBuilder().setUserId("buyer").build();
        getOrdersByUserIdResponse response = handler.getOrdersbyUserId(request);



        verify(orderDao, times(1)).save(argThat(
                a -> a.getItem_summary().containsAll(order1.getItem_summary())
                && a.getOrderId().equals(order1.getOrderId()))
        );
        verify(orderDao, times(1)).save(argThat(
                a -> a.getItem_summary().containsAll(order2.getItem_summary())
                        && a.getOrderId().equals(order2.getOrderId()))
        );
    }




    private static Order generateOrder(String id) {
        return Order.builder().buyer("buyer")
                .delivery_address("addr")
                .delivery_method("2-day Prime")
                .item_summary(ImmutableList.of(ItemInfo.builder().item_id("oaidjfo")
                        .item_name("jacket")
                        .price(39.99)
                        .quantity(3).build(), ItemInfo.builder().item_id("owoidsi")
                        .quantity(3)
                        .item_name("jeans")
                        .price(19.99).build()))
                .seller("seller")
                .orderId(id)
                .transactionId("daioijfaisdojfioa")
                .build();
    }
}
