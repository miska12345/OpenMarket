import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.common.collect.ImmutableList;
import io.grpc.Channel;
import io.openmarket.order.dao.OrderDao;
import io.openmarket.order.dao.OrderDaoImpl;
import io.openmarket.order.grpc.OrderGrpc;
import io.openmarket.order.grpc.OrderOuterClass;
import io.openmarket.order.model.ItemInfo;
import io.openmarket.order.model.Order;

import io.openmarket.server.MyCredential;

import java.util.List;


public class TestClient {
    private final OrderGrpc.OrderBlockingStub blockingStub;

    public TestClient(Channel channel) {
        blockingStub = OrderGrpc.newBlockingStub(channel);
    }

    public void pay() {
        AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().build();
        OrderDao dao = new OrderDaoImpl(dbClient, new DynamoDBMapper(dbClient));
        dao.save(Order.builder().buyer("buyer")
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
                .orderId("abcdefg")
                .transactionId("daioijfaisdojfioa")
                .build());
        dao.save(Order.builder().buyer("buyer")
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
                .orderId("abcdefghijklmn")
                .transactionId("daioijfaisdojfioa")
                .build());
        OrderOuterClass.getOrdersByUserIdResponse response = blockingStub.withCallCredentials(new MyCredential()).getOrdersByUserId(OrderOuterClass.getOrdersByUserIdRequest.newBuilder().setUserId("buyer").build());
        System.out.println(response);

    }


}