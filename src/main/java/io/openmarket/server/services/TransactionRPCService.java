package io.openmarket.server.services;

import io.grpc.stub.StreamObserver;
import io.openmarket.transaction.grpc.TransactionGrpc;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.service.TransactionServiceHandler;
import lombok.NonNull;

import javax.inject.Inject;

public class TransactionRPCService extends TransactionGrpc.TransactionImplBase {
    private final TransactionServiceHandler handler;

    @Inject
    public TransactionRPCService(@NonNull final TransactionServiceHandler handler) {
        this.handler = handler;
    }

    @Override
    public void processPayment(@NonNull final TransactionProto.PaymentRequest request,
                               @NonNull final StreamObserver<TransactionProto.PaymentResult> responseObserver) {
        responseObserver.onNext(handler.handlePayment(request));
        responseObserver.onCompleted();
    }

    @Override
    public void processQuery(@NonNull final TransactionProto.QueryRequest request,
                               @NonNull final StreamObserver<TransactionProto.QueryResult> responseObserver) {
        responseObserver.onNext(handler.handleQuery(request));
        responseObserver.onCompleted();
    }
}
