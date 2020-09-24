package io.openmarket.server.services;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.openmarket.event.grpc.EventProto;
import io.openmarket.event.grpc.StampEventGrpc;
import io.openmarket.stamp.service.StampEventServiceHandler;
import lombok.NonNull;

import javax.inject.Inject;

public class StampEventRPCService extends StampEventGrpc.StampEventImplBase {
    private final StampEventServiceHandler handler;

    @Inject
    public StampEventRPCService(@NonNull final StampEventServiceHandler handler) {
        this.handler = handler;
    }

    @Override
    public void createEvent(@NonNull final EventProto.CreateEventRequest request,
                                   @NonNull final StreamObserver<EventProto.CreateEventResult> resultStreamObserver) {
        resultStreamObserver.onNext(handler.handleCreateEvent(Context.current(), request));
        resultStreamObserver.onCompleted();
    }

    @Override
    public void deleteEvent(@NonNull final EventProto.DeleteEventRequest request,
                            @NonNull final StreamObserver<EventProto.DeleteEventResult> resultStreamObserver) {
        resultStreamObserver.onNext(handler.handleDeleteEvent(Context.current(), request));
        resultStreamObserver.onCompleted();
    }

    @Override
    public void redeem(@NonNull final EventProto.RedeemRequest request,
                            @NonNull final StreamObserver<EventProto.RedeemResult> resultStreamObserver) {
        resultStreamObserver.onNext(handler.handleRedeem(Context.current(), request));
        resultStreamObserver.onCompleted();
    }

    @Override
    public void getEvent(@NonNull final EventProto.GetEventRequest request,
                          @NonNull final StreamObserver<EventProto.GetEventResult> resultStreamObserver) {
        resultStreamObserver.onNext(handler.handleGetEvent(Context.current(), request));
        resultStreamObserver.onCompleted();
    }
}
