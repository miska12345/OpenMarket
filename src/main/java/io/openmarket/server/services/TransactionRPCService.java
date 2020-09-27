package io.openmarket.server.services;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.openmarket.server.config.InterceptorConfig;
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
        responseObserver.onNext(handler.handlePayment(Context.current(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void processQuery(@NonNull final TransactionProto.QueryRequest request,
                               @NonNull final StreamObserver<TransactionProto.QueryResult> responseObserver) {
        String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(Context.current());
        responseObserver.onNext(handler.handleQuery(userId, request));
        responseObserver.onCompleted();
    }

    @Override
    public void processRefund(@NonNull final TransactionProto.RefundRequest request,
                              @NonNull final StreamObserver<TransactionProto.RefundResult> responseObserver) {
        responseObserver.onNext(handler.handleRefund(Context.current(), request));
        responseObserver.onCompleted();
    }

    @Override
    public void processGetWallet(@NonNull final TransactionProto.GetWalletRequest request,
                              @NonNull final StreamObserver<TransactionProto.GetWalletResult> responseObserver) {
        String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(Context.current());
        responseObserver.onNext(handler.getWallet(userId, request));
        responseObserver.onCompleted();
    }
}
