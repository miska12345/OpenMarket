package io.openmarket.server;

import io.grpc.Channel;
import io.openmarket.transaction.grpc.TransactionGrpc;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.grpc.TransactionProto.QueryRequest;
import io.openmarket.transaction.grpc.TransactionProto.QueryResult;

public class TestClient {
    private final TransactionGrpc.TransactionBlockingStub blockingStub;

    public TestClient(Channel channel) {
        blockingStub = TransactionGrpc.newBlockingStub(channel).withCallCredentials(new MyCredential());
    }

    public void pay() {
        TransactionProto.PaymentRequest request = TransactionProto.PaymentRequest.newBuilder().setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .setRecipientId("321").setMoneyAmount(TransactionProto
                        .MoneyAmount.newBuilder().setCurrencyId("666").setAmount(3.13)).build();
        System.out.println(blockingStub.processPayment(request));
    }

    public void doQuery() {
        QueryRequest request = QueryRequest.newBuilder().setType(QueryRequest.QueryType.PAYER_ID).setParam("456").build();
        QueryResult result = blockingStub.processQuery(request);
        System.out.println(result.getItemsList());
    }

    public void doGetWallet() {
        TransactionProto.GetWalletRequest req = TransactionProto.GetWalletRequest.newBuilder().build();
        TransactionProto.GetWalletResult result = blockingStub.processGetWallet(req);
        System.out.println(result.getCurrenciesMap());
    }
}
