package io.openmarket.server.services;

import io.grpc.stub.StreamObserver;
import io.openmarket.order.OrderServiceHandler;
import io.openmarket.order.grpc.OrderGrpc;
import io.openmarket.order.grpc.OrderOuterClass;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.grpc.OrganizationGrpc;
import io.openmarket.organization.grpc.OrganizationOuterClass;
import lombok.NonNull;

import javax.inject.Inject;

public class OrderRPCService extends OrderGrpc.OrderImplBase {
    private final OrderServiceHandler handler;

    @Inject
    public OrderRPCService(@NonNull final OrderServiceHandler handler) {
        this.handler = handler;
    }


    @Override
    public void getOrdersByUserId(OrderOuterClass.getOrdersByUserIdRequest request, StreamObserver<OrderOuterClass.getOrdersByUserIdResponse> responseObserver) {
        responseObserver.onNext(handler.getOrdersbyUserId(request));
        responseObserver.onCompleted();
    }

}
