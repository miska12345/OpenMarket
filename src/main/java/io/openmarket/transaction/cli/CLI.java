package io.openmarket.transaction.cli;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import io.openmarket.transaction.dao.dynamodb.TransactionDaoImpl;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.service.TransactionServiceHandler;

import java.util.Scanner;

public class CLI {
    private static Scanner scanner;
    private static TransactionServiceHandler handler;
    public static void main(String[] args) {
        initialize();
        handleInput();
    }

    private static void handleInput() {
        do {
            System.out.println("Enter ops [query id, query payer, transaction]:");
            String ops = scanner.nextLine();
            if (ops.equals("query payer")) {
                handleQuery();
            } else if (ops.equals("query id")) {
                handleQuery2();
            } else if (ops.equals("transaction")) {
                handleTransaction();
            }
        } while (true);
    }

    private static void initialize() {
        scanner = new Scanner(System.in);
        AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().build();
        SQSTransactionTaskPublisher sqsPublisher = new SQSTransactionTaskPublisher(AmazonSQSClientBuilder.standard().build());
        handler = new TransactionServiceHandler(new TransactionDaoImpl(dbClient, new DynamoDBMapper(dbClient)),
                sqsPublisher, "https://sqs.us-west-2.amazonaws.com/185046651126/TransactionTaskQueue");
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
        System.out.println("Enter payerId");
        String payer = scanner.nextLine();
        TransactionProto.PaymentRequest payment = TransactionProto.PaymentRequest.newBuilder()
                .setCurrencyId("123")
                .setAmount(3.21)
                .setPayerId(payer)
                .setRecipientId("321")
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        String id = handler.handlePayment(payment).getTransactionId();
        System.out.println(String.format("TransactionID: %s", id));
    }
}
