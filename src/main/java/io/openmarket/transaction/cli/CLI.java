package io.openmarket.transaction.cli;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import io.openmarket.transaction.dao.dynamodb.TransactionDaoImpl;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.service.TransactionServiceHandler;

import java.util.Scanner;

public class CLI {
    private static Scanner scanner;
    private static TransactionServiceHandler handler;
    public static void main(String[] args) {
        initialize();
        //handleQuery();
        handleTransaction();
    }

    private static void initialize() {
        scanner = new Scanner(System.in);
        AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().build();
        handler = new TransactionServiceHandler(new TransactionDaoImpl(dbClient, new DynamoDBMapper(dbClient)));
    }

    private static void handleQuery() {
        System.out.println("Enter user id:");
        TransactionProto.QueryRequest request = TransactionProto.QueryRequest.newBuilder()
                .setType(TransactionProto.QueryRequest.QueryType.PAYER_ID)
                .setParam(scanner.nextLine()).build();
        TransactionProto.QueryResult result = handler.handleQuery(request);
        System.out.println(result.getItemsList());
    }

    private static void handleTransaction() {
        TransactionProto.PaymentRequest payment = TransactionProto.PaymentRequest.newBuilder()
                .setCurrencyId("123")
                .setAmount(3.21)
                .setPayerId("123")
                .setRecipientId("321")
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        handler.handlePayment(payment);
    }
}
