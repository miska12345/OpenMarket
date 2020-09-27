package io.openmarket.order;


import io.openmarket.order.dao.OrderDao;
import io.openmarket.order.grpc.OrderOuterClass;
import io.openmarket.order.model.ItemInfo;
import io.openmarket.order.model.Order;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.*;

@Log4j2
public class OrderServiceHandler {
    protected OrderDao orderDao;

    @Inject
    public OrderServiceHandler(OrderDao orderDao) {
        this.orderDao = orderDao;
    }

    public void addOrderHistory(Order order) {
        if (order.getOrderId() == null || order.getOrderId().isEmpty()) {
            throw new IllegalArgumentException("Order ID is missing!");
        }
        if (order.getSeller() == null || order.getSeller().isEmpty()) {
            throw new IllegalArgumentException("Seller ID is missing!");
        }
        if (order.getBuyer() == null || order.getBuyer().isEmpty()) {
            throw new IllegalArgumentException("Buyer ID is missing!");
        }
        if (order.getTransactionId() == null || order.getTransactionId().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is missing!");
        }
        if (order.getItem_summary() == null || order.getItem_summary().isEmpty()) {
            throw new IllegalArgumentException("Item is missing!");
        }
        if (order.getDelivery_address() == null) order.setDelivery_address("");
        if (order.getDelivery_method() == null) order.setDelivery_method("");
        if (order.getPayment_method() == null) order.setPayment_method("");


        double total = 0;
        for (ItemInfo item:order.getItem_summary()) {
            total += (item.getPrice()*item.getQuantity());
        }
        order.setTotal(total);
        orderDao.save(order);
    }

    public OrderOuterClass.getOrdersByUserIdResponse getOrdersbyUserId (OrderOuterClass.getOrdersByUserIdRequest params) {
        List<Order> res = orderDao.getOrdersById(params.getUserId());
        List<OrderOuterClass.OrderMetaData> orders = new ArrayList<>();
        for (Order order: res) {
            orders.add(order2orderMD(order));
        }

        return OrderOuterClass.getOrdersByUserIdResponse.newBuilder().addAllOrders(orders).build();

    }

    private OrderOuterClass.OrderMetaData order2orderMD(Order order) {
        List<OrderOuterClass.ItemInfo> itemInfos = new ArrayList<>();
        for (ItemInfo item: order.getItem_summary()) {
            itemInfos.add(OrderOuterClass.ItemInfo.newBuilder()
                    .setPrice(item.getPrice())
                    .setItemName(item.getItem_name())
                    .setItemId(item.getItem_id())
                    .setQuantity(item.getQuantity())
                    .build());
        }
        return OrderOuterClass.OrderMetaData.newBuilder()
                .setBuyer(order.getBuyer())
                .setSeller(order.getSeller())
                .setCreatedAt(order.getCreatedAt())
                .setDeliveryAddress(order.getDelivery_address())
                .setDeliveryMethod(order.getDelivery_method())
                .setOrderId(order.getOrderId())
                .setTransactionId(order.getTransactionId())
                .setTotal(order.getTotal())
                .setTransactionId(order.getTransactionId())
                .addAllItemSummary(itemInfos)
                .build();
    }




}

