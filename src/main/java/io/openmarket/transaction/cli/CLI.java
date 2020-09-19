package io.openmarket.transaction.cli;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import io.grpc.Context;
import io.openmarket.server.config.InterceptorConfig;
import io.openmarket.transaction.dao.dynamodb.TransactionDaoImpl;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.service.TransactionServiceHandler;

import java.util.Scanner;

public class CLI {
    private static Scanner scanner;
    private static TransactionServiceHandler handler;
    private static Context context;
    public static void main(String[] args) {
        initialize();
        handleInput();
    }

    private static void handleInput() {
        do {
            System.out.println("Enter ops [as, query id, query payer, pay, refund]:");
            String ops = scanner.nextLine();
            if (ops.equals("query payer")) {
                handleQuery();
            } else if (ops.equals("query id")) {
                handleQuery2();
            } else if (ops.equals("pay")) {
                handleTransaction();
            } else if (ops.equals("refund")) {
                handleRefund();
            } else if (ops.equals("as")) {
                System.out.println("Enter your id");
                context = Context.current().withValue(InterceptorConfig.USER_NAME_CONTEXT_KEY, scanner.nextLine());
            }
        } while (true);
    }

    private static void initialize() {
        scanner = new Scanner(System.in);
        AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().build();
        SQSTransactionTaskPublisher sqsPublisher = new SQSTransactionTaskPublisher(AmazonSQSClientBuilder.standard().build());
        handler = new TransactionServiceHandler(new TransactionDaoImpl(dbClient, new DynamoDBMapper(dbClient)),
                sqsPublisher, "https://sqs.us-west-2.amazonaws.com/185046651126/TransactionTaskQueue");
        context = Context.current();
    }

    private static void handleQuery() {
        System.out.println("Enter user id:");
        TransactionProto.QueryRequest request = TransactionProto.QueryRequest.newBuilder()
                .setType(TransactionProto.QueryRequest.QueryType.PAYER_ID)
                .setParam(scanner.nextLine()).build();
        TransactionProto.QueryResult result = handler.handleQuery(request);
        System.out.println(result.getItemsList());
    }

    private static void handleQuery2() {
        System.out.println("Enter transaction id:");
        TransactionProto.QueryRequest request = TransactionProto.QueryRequest.newBuilder()
                .setType(TransactionProto.QueryRequest.QueryType.TRANSACTION_ID)
                .setParam(scanner.nextLine()).build();
        TransactionProto.QueryResult result = handler.handleQuery(request);
        System.out.println(result.getItemsList());
    }

    private static void handleTransaction() {
        System.out.println("Enter recipientId");
        String recipientId = scanner.nextLine();
        TransactionProto.PaymentRequest payment = TransactionProto.PaymentRequest.newBuilder()
                .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setCurrencyId("666").setAmount(3.0))
                .setRecipientId(recipientId)
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        String id = handler.handlePayment(context, payment).getTransactionId();
        System.out.println(String.format("TransactionID: %s", id));
    }

    private static void handleRefund() {
        System.out.println("Enter transactionId that you are the recipient");
        String transacId = scanner.nextLine();
        TransactionProto.RefundRequest request = TransactionProto.RefundRequest.newBuilder().setTransactionId(transacId).setReason("a").build();
        System.out.println(handler.handleRefund(context, request));
    }
}
