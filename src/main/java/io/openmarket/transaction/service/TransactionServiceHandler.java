package io.openmarket.transaction.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.exception.InvalidTransactionException;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionTask;
import io.openmarket.transaction.model.TransactionType;
import io.openmarket.transaction.utils.TransactionUtils;
import io.openmarket.utils.TimeUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_ERROR_TYPE;
import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_STATUS;

@Log4j2
public class TransactionServiceHandler {
    private final TransactionDao transactionDao;
    private final SQSTransactionTaskPublisher sqsPublisher;
    private final String queueURL;

    @Inject
    public TransactionServiceHandler(@NonNull final TransactionDao transactionDao,
                                     @NonNull final SQSTransactionTaskPublisher sqsPublisher,
                                     @NonNull final String queueURL) {
        this.transactionDao = transactionDao;
        this.sqsPublisher = sqsPublisher;
        this.queueURL = queueURL;
        log.info("TransactionServiceHandler started");
    }

    public TransactionProto.PaymentResult handlePayment(@NonNull final TransactionProto.PaymentRequest request) {
        if (!isPaymentRequestValid(request)) {
            log.error("Payment request is invalid: {}", request);
            throw new IllegalArgumentException(String.format("The given request contains invalid params: %s",
                    request));
        }
        final String transactionID = TransactionUtils.generateTransactionID();
        final Transaction transaction = Transaction.builder()
                .transactionId(transactionID)
                .recipientId(request.getRecipientId())
                .payerId(request.getPayerId())
                .currencyId(request.getMoneyAmount().getCurrencyId())
                .amount(request.getMoneyAmount().getAmount())
                .status(TRANSACTION_INITIAL_STATUS)
                .type(TransactionType.valueOf(request.getType().toString()))
                .note(request.getNote())
                .error(TRANSACTION_INITIAL_ERROR_TYPE)
                .build();
        transactionDao.save(transaction);
        sqsPublisher.publish(queueURL, new TransactionTask(transactionID));
        log.info("Created a new transaction: {}", transaction);
        return TransactionProto.PaymentResult.newBuilder().setTransactionId(transactionID).build();
    }

    public TransactionProto.QueryResult handleQuery(@NonNull final TransactionProto.QueryRequest request) {
        log.info("Handling transaction query with request: {}", request);
        List<Transaction> matchingTransactions = Collections.emptyList();
        try {
            if (request.getType().equals(TransactionProto.QueryRequest.QueryType.TRANSACTION_ID)) {
                matchingTransactions = ImmutableList.of(getTransactionByID(request.getParam()));
            } else if (request.getType().equals(TransactionProto.QueryRequest.QueryType.PAYER_ID)) {
                matchingTransactions = getAllTransactionsForPayer(request.getParam());
                log.info("Found {} transactions", matchingTransactions.size());
            }
        } catch (InvalidTransactionException e) {
            log.error("InvalidTransactionException occurred with request type {}, param '{}'",
                    request.getType(), request.getParam(), e);
        }
        return convertTransactionToQueryResult(matchingTransactions);
    }

    private List<Transaction> getAllTransactionsForPayer(@NonNull final String payerId) {
        if (payerId.isEmpty()) {
            throw new IllegalArgumentException("payerId cannot be empty");
        }
        Map<String, AttributeValue> lastEvaluatedKey = null;
        final List<Transaction> result = new ArrayList<>();
        do {
            lastEvaluatedKey = transactionDao.getTransactionForPayer(payerId, result, lastEvaluatedKey);
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
        return result;
    }

    private Transaction getTransactionByID(final String transactionID) {
        if (transactionID.isEmpty()) {
            throw new IllegalArgumentException(String.format("Invalid transaction id found: %s",
                    transactionID));
        }
        return transactionDao.load(transactionID)
                .orElseThrow(() -> new InvalidTransactionException(String.format("Transaction Id '%s' is invalid",
                        transactionID)));
    }

    private TransactionProto.QueryResult convertTransactionToQueryResult(final List<Transaction> transactions) {
        return TransactionProto.QueryResult.newBuilder().addAllItems(transactions.stream().map(
                t -> TransactionProto.QueryResultItem.newBuilder()
                .setTransactionId(t.getTransactionId())
                .setCreatedAt(TimeUtils.formatDate(t.getCreatedAt()))
                .setStatus(TransactionProto.QueryResultItem.Status.valueOf(t.getStatus().toString()))
                .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
                        .setCurrencyId(t.getCurrencyId())
                        .setAmount(t.getAmount())
                )
                .setPayerId(t.getPayerId())
                .setRecipientId(t.getRecipientId())
                .setType(TransactionProto.QueryResultItem.Type.valueOf(t.getType().toString()))
                .build()).collect(Collectors.toList())).build();
    }

    @VisibleForTesting
    protected boolean isPaymentRequestValid(final TransactionProto.PaymentRequest request) {
        return !request.getMoneyAmount().getCurrencyId().isEmpty()
                && request.getMoneyAmount().getAmount() > 0
                && !request.getRecipientId().isEmpty()
                && !request.getRecipientId().isEmpty()
                && !request.getPayerId().isEmpty()
                && !request.getPayerId().equals(request.getRecipientId());
    }
}
