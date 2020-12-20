package io.openmarket.server;

import io.grpc.Channel;
import io.openmarket.event.grpc.EventProto;
import io.openmarket.event.grpc.StampEventGrpc;

public class TestClientEvent {
    private final StampEventGrpc.StampEventBlockingStub blockingStub;

    public TestClientEvent(Channel channel) {
        blockingStub = StampEventGrpc.newBlockingStub(channel).withCallCredentials(new MyCredential());
    }

    public void create() {
        EventProto.CreateEventRequest request = EventProto.CreateEventRequest.newBuilder()
                .setCurrency("666")
                .setExpiresAt("2020-10-01T12:00:00Z")
                .setTotalAmount(100.0)
                .setRewardAmount(1.0)
                .build();
        EventProto.CreateEventResult result = blockingStub.createEvent(request);
        System.out.println(result);
    }

    public void delete(String eventId) {
        EventProto.DeleteEventRequest request = EventProto.DeleteEventRequest.newBuilder()
                .setEventId(eventId)
                .build();

        EventProto.DeleteEventResult result = blockingStub.deleteEvent(request);
        System.out.println(result.getError());
    }

    public void doRedeem(String eventId) {
        EventProto.RedeemRequest req = EventProto.RedeemRequest.newBuilder().setEventId(eventId).build();
        EventProto.RedeemResult result = blockingStub.redeem(req);
        System.out.println(result);
    }
}
