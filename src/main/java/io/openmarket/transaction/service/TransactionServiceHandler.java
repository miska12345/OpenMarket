package io.openmarket.transaction.service;

import com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import io.grpc.Context;
import io.openmarket.config.NewAccountConfig;
import io.openmarket.server.config.InterceptorConfig;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.exception.InvalidTransactionException;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionErrorType;
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.model.TransactionTask;
import io.openmarket.transaction.model.TransactionType;
import io.openmarket.transaction.utils.TransactionUtils;
import io.openmarket.utils.TimeUtils;
import io.openmarket.wallet.dao.dynamodb.WalletDao;
import io.openmarket.wallet.model.Wallet;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_ERROR_TYPE;
import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_STATUS;

@Log4j2
public class TransactionServiceHandler {
    private static final Gson GSON = new Gson();
    private static final String INVALID_REFUND_REQUEST_ERR_MSG = "The given refund request is invalid";
    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final SQSTransactionTaskPublisher sqsPublisher;
    private final String queueURL;

    @Inject
    public TransactionServiceHandler(@NonNull final TransactionDao transactionDao,
                                     @NonNull final WalletDao walletDao,
                                     @NonNull final SQSTransactionTaskPublisher sqsPublisher,
                                     @NonNull final String queueURL) {
        this.transactionDao = transactionDao;
        this.walletDao = walletDao;
        this.sqsPublisher = sqsPublisher;
        this.queueURL = queueURL;
        log.info("TransactionServiceHandler started");
    }
    
    public TransactionProto.PaymentResult handlePayment(@NonNull final Context context,
                                                        @NonNull final TransactionProto.PaymentRequest request) {
        final String payerId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(context);
        final String transactionId = createPayment(payerId, request);
        return TransactionProto.PaymentResult.newBuilder().setTransactionId(transactionId).build();
    }

    public TransactionProto.QueryResult handleQuery(@NonNull final String userId,
                                                    @NonNull final TransactionProto.QueryRequest request) {
        log.info("Handling transaction query with request: {}", request);
        List<Transaction> matchingTransactions = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;
        try {
            switch (request.getType()) {
                case TRANSACTION_ID:
                    matchingTransactions = ImmutableList.of(getTransactionByID(request.getParam()));
                    break;
                case PAYER_ID:
                    lastEvaluatedKey = transactionDao.getTransactionForPayer(userId, matchingTransactions,
                            convertToExclusiveStartKey(request.getExclusiveStartKey()));
                    break;
                case RECIPIENT_ID:
                    lastEvaluatedKey = transactionDao.getTransactionForRecipient(userId, matchingTransactions,
                        convertToExclusiveStartKey(request.getExclusiveStartKey()));
                    break;
                case ALL:
                    // blablabla
                default:
                    log.warn("Transaction query has invalid type: {}", request.getType());
            }
        } catch (InvalidTransactionException e) {
            log.error("InvalidTransactionException occurred with request type {}, param '{}'",
                    request.getType(), request.getParam(), e);
        }
        log.info("Found {} transactions", matchingTransactions.size());
        return convertTransactionToQueryResult(matchingTransactions, lastEvaluatedKey);
    }

    public TransactionProto.RefundResult handleRefund(@NonNull final Context context,
                                                      @NonNull final TransactionProto.RefundRequest request) {
        final String currentUser = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(context);
        log.info("Handling transaction refund with request: {} for user '{}'", request, currentUser);
        if (!isRefundRequestValid(request)) {
            log.error("Refund request with transanctionId '{}' is invalid", request.getTransactionId());
            throw new IllegalArgumentException(INVALID_REFUND_REQUEST_ERR_MSG);
        }
        final Transaction transaction = transactionDao.load(request.getTransactionId())
                .orElseThrow(() ->
                        new IllegalArgumentException(INVALID_REFUND_REQUEST_ERR_MSG));

        if (!transaction.getRecipientId().equals(currentUser)
                || !transaction.getStatus().equals(TransactionStatus.COMPLETED)
                || transaction.getType().equals(TransactionType.REFUND)) {
            log.error("Failed to create refund with request {}", request);
            throw new IllegalArgumentException(INVALID_REFUND_REQUEST_ERR_MSG);
        }

        final Transaction refundTransaction = Transaction.builder()
                .transactionId(TransactionUtils.generateTransactionID())
                .type(TransactionType.REFUND)
                .currencyId(transaction.getCurrencyId())
                .amount(transaction.getAmount())
                .note(request.getReason())
                .status(TransactionStatus.PENDING)
                .payerId(transaction.getRecipientId())
                .recipientId(transaction.getPayerId())
                .refundTransacIds(ImmutableList.of(transaction.getTransactionId()))
                .error(TransactionErrorType.NONE)
                .build();
        transaction.getRefundTransacIds().add(refundTransaction.getTransactionId());
        transaction.setStatus(TransactionStatus.REFUND_STARTED);
        try {
            createRefundTransactionPair(transaction, refundTransaction);
        } catch (ConditionalCheckFailedException e) {
            log.error("Exception encountered while creating refund transaction for '{}'",
                    transaction.getTransactionId(), e);
            throw new IllegalStateException("Refund not created, try again later.");
        }
        return convertTransactionToRefundResult(transactionDao.load(refundTransaction.getTransactionId()).get());
    }

    public TransactionStatus getTransactionStatus(@NonNull final String transactionId) {
        final Transaction transaction = transactionDao.load(transactionId)
                .orElseThrow(() -> new InvalidTransactionException("Transaction ID is invalid"));
        return transaction.getStatus();
    }

    public String createPayment(final String payerId, TransactionProto.PaymentRequest request) {
        final Transaction transaction = createPaymentDraft(payerId, request);
        transactionDao.save(transaction);
        sendTransactionToProcessorQueue(transaction);
        return transaction.getTransactionId();
    }

    public Stepper createPaymentStepper(final String payerId, TransactionProto.PaymentRequest request) {
        final Transaction transaction = createPaymentDraft(payerId, request);
        transactionDao.save(transaction);
        return new Stepper(transaction);
    }

    public Transaction createPaymentDraft(final String payerId, TransactionProto.PaymentRequest request) {
        if (!isPaymentRequestValid(request)
                || request.getRecipientId().equals(payerId)) {
            log.error("Payment requested by '{}' is invalid: {}", payerId, request);
            throw new IllegalArgumentException(String.format("The given request contains invalid params: %s",
                    request));
        }
        final Transaction transaction = Transaction.builder()
                .transactionId(TransactionUtils.generateTransactionID())
                .payerId(payerId)
                .recipientId(request.getRecipientId())
                .currencyId(request.getMoneyAmount().getCurrencyId())
                .amount(request.getMoneyAmount().getAmount())
                .status(TRANSACTION_INITIAL_STATUS)
                .type(TransactionType.valueOf(request.getType().toString()))
                .note(request.getNote())
                .error(TRANSACTION_INITIAL_ERROR_TYPE)
                .build();
        return transaction;
    }

    public TransactionProto.GetWalletResult getWallet(final String userId, TransactionProto.GetWalletRequest request) {
        final Optional<Wallet> wallet = walletDao.load(userId);
        if (!wallet.isPresent()) {
            log.error("UserId {} is invalid", userId);
            throw new IllegalArgumentException(String.format("UserId '%s' is invalid", userId));
        }
        final Map<String, Double> filteredMap = wallet.get().getCoins()
                .entrySet().stream().filter(x -> x.getValue() > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        log.info("User '{}' successfully got wallet data," +
                " result contains {} entries", userId, filteredMap.size());
        return TransactionProto.GetWalletResult.newBuilder()
                .putAllCurrencies(filteredMap)
                .build();
    }

    public void createWalletForUser(final String userId) {
        if (walletDao.load(userId).isPresent()) {
            log.error("Tried to create wallet for user {} that already exists", userId);
            throw new IllegalArgumentException(String.format("User with ID '%s' already has wallet!"));
        }
        walletDao.save(Wallet.builder().ownerId(userId).coins(NewAccountConfig.INITIAL_PROFILE).build());
    }

    private void createRefundTransactionPair(final Transaction source, final Transaction refundTransaction) {
        transactionDao.transactionWrite(new TransactionWriteRequest()
                .addUpdate(source)
                .addPut(refundTransaction)
        );
        sqsPublisher.publish(queueURL, new TransactionTask(refundTransaction.getTransactionId()));
    }

    private void sendTransactionToProcessorQueue(final Transaction transaction) {
        sqsPublisher.publish(queueURL, new TransactionTask(transaction.getTransactionId()));
        log.info("Created a new transaction: {}", transaction);
    }

    private Map<String, AttributeValue> convertToExclusiveStartKey(final String exclusiveStartKeyStr) {
        Map<String, AttributeValue> exclusiveStartKey = null;
        if (!exclusiveStartKeyStr.isEmpty()) {
            try {
                Type type = new TypeToken<Map<String, AttributeValue>>(){}.getType();
                exclusiveStartKey = GSON.fromJson(exclusiveStartKeyStr, type);
            } catch (Exception e) {
                log.error("Failed to parse exclusiveStartKey {}", exclusiveStartKeyStr);
            }
        }
        return exclusiveStartKey;
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

    private TransactionProto.RefundResult convertTransactionToRefundResult(final Transaction refundTransaction) {
        return TransactionProto.RefundResult.newBuilder()
                .setError(TransactionProto.Error.newBuilder()
                        .setCategory(TransactionProto.ErrorCategory.INSUFFICIENT_BALANCE))
                .setRefund(TransactionProto.PaymentRefund.newBuilder()
                        .setRefundId(refundTransaction.getTransactionId())
                        .setCreatedAt(TimeUtils.formatDate(refundTransaction.getCreatedAt()))
                        .setStatus(TransactionProto.Status.valueOf(refundTransaction.getStatus().toString()))
                        .setReason(refundTransaction.getNote())
                        .setTransactionId(refundTransaction.getRefundTransacIds().stream().findFirst().get())
                        .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
                                .setCurrencyId(refundTransaction.getCurrencyId())
                                .setAmount(refundTransaction.getAmount())
                                .build())
                        .build())
                .build();
    }

    private TransactionProto.QueryResult convertTransactionToQueryResult(final List<Transaction> transactions,
                                                                         final Map<String, AttributeValue> lastEvaluatedKey) {
        return TransactionProto.QueryResult.newBuilder().addAllItems(transactions.stream().map(
                t -> TransactionProto.QueryResultItem.newBuilder()
                .setTransactionId(t.getTransactionId())
                .setCreatedAt(TimeUtils.formatDate(t.getCreatedAt()))
                .setStatus(TransactionProto.Status.valueOf(t.getStatus().toString()))
                .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
                        .setCurrencyId(t.getCurrencyId())
                        .setAmount(t.getAmount())
                )
                .setPayerId(t.getPayerId())
                .setRecipientId(t.getRecipientId())
                .setType(TransactionProto.QueryResultItem.Type.valueOf(t.getType().toString()))
                        .setError(TransactionProto.ErrorCategory.valueOf(t.getError().toString()))
                        .setNote(t.getNote())
                .build()).collect(Collectors.toList()))
                .setLastEvaluatedKey(lastEvaluatedKey == null ? "" : GSON.toJson(lastEvaluatedKey))
                .build();
    }

    @VisibleForTesting
    protected boolean isPaymentRequestValid(final TransactionProto.PaymentRequest request) {
        return !request.getMoneyAmount().getCurrencyId().trim().isEmpty()
                && request.getMoneyAmount().getAmount() > 0
                && !request.getRecipientId().trim().isEmpty();
    }

    @VisibleForTesting
    protected boolean isRefundRequestValid(final TransactionProto.RefundRequest request) {
        return !request.getTransactionId().trim().isEmpty();
    }

    /**
     * A stepper that allows committing or aborting a transaction before it is processed.
     */
    public class Stepper {
        @Getter
        private final Transaction transaction;
        private boolean hasCommitted = false;
        public Stepper(@NonNull final Transaction transactionId) {
            this.transaction = transactionId;
        }

        public void commit() {
            sendTransactionToProcessorQueue(this.transaction);
            hasCommitted = true;
        }

        public void abort() {
            if (!hasCommitted) {
                transactionDao.delete(this.transaction);
            }
        }
    }
}
