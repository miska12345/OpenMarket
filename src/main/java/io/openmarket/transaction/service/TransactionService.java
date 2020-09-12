package io.openmarket.transaction.service;

import io.grpc.stub.StreamObserver;
import io.openmarket.transaction.grpc.TransactionGrpc;
import io.openmarket.transaction.grpc.TransactionProto;

public class TransactionService extends TransactionGrpc.TransactionImplBase {
    @Override
    public void transfer(TransactionProto.TransferRequest request,
                         StreamObserver<TransactionProto.TransferResult> responseObserver) {
    }
}
