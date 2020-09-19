package io.openmarket.transaction.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.google.common.collect.ImmutableList;
import io.grpc.Context;
import io.openmarket.server.config.InterceptorConfig;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionErrorType;
import io.openmarket.transaction.model.TransactionStatus;
import io.openmarket.transaction.model.TransactionType;
import io.openmarket.transaction.utils.TransactionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.AdditionalMatchers;
import org.mockito.stubbing.Answer;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_ERROR_TYPE;
import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_STATUS;
import static io.openmarket.transaction.grpc.TransactionProto.QueryRequest.QueryType.TRANSACTION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TransactionServiceHandlerTest {
    private static final String QUEUE_URL = "queue_url";
    private static final String CURRENCY_ID = "100";
    private static final double AMOUNT = 8.0;
    private static final String MY_ID = "000";
    private static final String RECIPIENT_ID = "002";
    private static final String OTHER_ID = "003";
    private static final String NOTE = "Hello";
    private static final String TEST_TRANSACTION_ID = "123";

    private TransactionDao transactionDao;
    private SQSTransactionTaskPublisher sqsPublisher;
    private TransactionServiceHandler handler;

    @BeforeEach
    public void setup() {
        this.transactionDao = mock(TransactionDao.class);
        this.sqsPublisher = mock(SQSTransactionTaskPublisher.class);
        this.handler = new TransactionServiceHandler(transactionDao, sqsPublisher, QUEUE_URL);
    }

    @Test
    public void when_Query_By_ID_And_Exists_Then_Return() {
        Transaction mockTransaction = generateTransaction(TransactionUtils.generateTransactionID());
        TransactionProto.QueryRequest request = TransactionProto.QueryRequest.newBuilder()
                .setType(TRANSACTION_ID).setParam(mockTransaction.getTransactionId()).build();
        when(transactionDao.load(mockTransaction.getTransactionId())).thenReturn(Optional.of(mockTransaction));
        TransactionProto.QueryResult result = handler.handleQuery(request);
        assertEquals(1, result.getItemsCount());

        TransactionProto.QueryResultItem item = result.getItemsList().get(0);
        assertEquals(mockTransaction.getTransactionId(), item.getTransactionId());
        assertEquals(mockTransaction.getTransactionId(), item.getTransactionId());
        assertEquals(mockTransaction.getCurrencyId(), item.getMoneyAmount().getCurrencyId());
        assertEquals(mockTransaction.getAmount(), item.getMoneyAmount().getAmount());
        assertEquals(mockTransaction.getStatus().toString(), item.getStatus().toString());
        assertEquals(mockTransaction.getPayerId(), item.getPayerId());
        assertEquals(mockTransaction.getRecipientId(), item.getRecipientId());
    }

    @Test
    public void when_Query_With_Invalid_ID_Then_Throw_IllegalArgumentException() {
        TransactionProto.QueryRequest request = TransactionProto.QueryRequest.newBuilder()
                .setType(TRANSACTION_ID).setParam("").build();
        assertThrows(IllegalArgumentException.class, () -> handler.handleQuery(request));
    }

    @ParameterizedTest
    @MethodSource("getQueryParam")
    public void test_Query(TransactionProto.QueryRequest.QueryType type, String param, int count, boolean exists) {
        if (exists) {
            Set<Transaction> transactionSet = new HashSet<>();
            for (int i = 0; i < count; i++) {
                String transactionId = TransactionUtils.generateTransactionID();
                if (type.equals(TRANSACTION_ID)) {
                    transactionId = param;
                }
                transactionSet.add(generateTransaction(transactionId));
            }
            for (Transaction transaction : transactionSet) {
                when(transactionDao.load(transaction.getTransactionId())).thenReturn(Optional.of(transaction));
            }
            when(transactionDao.getTransactionForPayer(anyString(), any(), any()))
                    .thenAnswer((Answer<Map<String, AttributeValue>>) invocation -> {
                Collection<Transaction> collection = invocation.getArgument(1);
                collection.addAll(transactionSet);
                return null;
            });
        } else {
            when(transactionDao.load(anyString())).thenReturn(Optional.empty());
            when(transactionDao.getTransactionForPayer(anyString(), any(), any())).thenReturn(Collections.emptyMap());
        }
        TransactionProto.QueryResult result =
                handler.handleQuery(TransactionProto.QueryRequest.newBuilder().setType(type).setParam(param).build());
        assertEquals(count, result.getItemsList().size());
    }

    private static Stream<Arguments> getQueryParam() {
        return Stream.of(
                Arguments.of(TRANSACTION_ID, "123", 1, true),
                Arguments.of(TRANSACTION_ID, "123", 0, false),
                Arguments.of(TransactionProto.QueryRequest.QueryType.PAYER_ID, MY_ID, 3, true),
                Arguments.of(TransactionProto.QueryRequest.QueryType.PAYER_ID, MY_ID, 0, false)
        );
    }

    @Test
    public void check_Payment_DB_Entry_And_SQS_Msg() {
        TransactionProto.PaymentRequest request = TransactionProto.PaymentRequest.newBuilder()
                .setRecipientId(RECIPIENT_ID)
                .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setAmount(3.13).setCurrencyId(CURRENCY_ID))
                .setNote("")
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        TransactionProto.PaymentResult result = handler.handlePayment(getContext(), request);

        verify(transactionDao, times(1)).save(argThat(
                a -> a.getTransactionId().equals(result.getTransactionId())
                        && a.getPayerId().equals(MY_ID)
                        && a.getRecipientId().equals(request.getRecipientId())
                        && a.getStatus().equals(TRANSACTION_INITIAL_STATUS)
                        && a.getError().equals(TRANSACTION_INITIAL_ERROR_TYPE)
                        && a.getCurrencyId().equals(request.getMoneyAmount().getCurrencyId())
                        && a.getAmount().equals(request.getMoneyAmount().getAmount())
                        && a.getNote().equals(request.getNote())
                )
        );

        verify(sqsPublisher, times(1)).publish(eq(QUEUE_URL),
                argThat(a -> a.getTransactionId().equals(result.getTransactionId())));
    }

    @ParameterizedTest
    @MethodSource("getInvalidPaymentRequests")
    public void check_Invalid_Payment_Request(TransactionProto.PaymentRequest request) {
        assertThrows(IllegalArgumentException.class, () -> handler.handlePayment(getContext(), request));
    }

    @ParameterizedTest
    @MethodSource("getQueryIllegalArgumentParam")
    public void test_Query_IllegalArgumentException(TransactionProto.QueryRequest.QueryType type, String param) {
        assertThrows(IllegalArgumentException.class,
                () -> handler.handleQuery(TransactionProto.QueryRequest.newBuilder().setType(type).setParam(param).build()));
    }

    private static Stream<Arguments> getQueryIllegalArgumentParam() {
        return Stream.of(
                Arguments.of(TRANSACTION_ID, ""),
                Arguments.of(TransactionProto.QueryRequest.QueryType.PAYER_ID, "")
        );
    }

    @Test
    public void do_Throw_IllegalArgumentException_With_Invalid_Transfer_Request() {
        TransactionProto.PaymentRequest request = TransactionProto.PaymentRequest.newBuilder()
                .setRecipientId(MY_ID)
                .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setCurrencyId("321").setAmount(3.13))
                .setNote("")
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        assertThrows(IllegalArgumentException.class, () ->  handler.handlePayment(getContext(), request));
        verify(transactionDao, times(0)).save(any());
    }

    private static Stream<Arguments> getInvalidPaymentRequests() {
        return Stream.of(
                Arguments.of(TransactionProto.PaymentRequest.newBuilder()
                        .setRecipientId(RECIPIENT_ID)
                        .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setCurrencyId("")).build()),
                Arguments.of(TransactionProto.PaymentRequest.newBuilder()
                        .setRecipientId(RECIPIENT_ID)
                        .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setCurrencyId(CURRENCY_ID).setAmount(0)).build()),
                Arguments.of(TransactionProto.PaymentRequest.newBuilder()
                        .setRecipientId(RECIPIENT_ID)
                        .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setCurrencyId(CURRENCY_ID).setAmount(-1)).build()),
                Arguments.of(TransactionProto.PaymentRequest.newBuilder()
                        .setRecipientId(MY_ID)
                        .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder().setCurrencyId(CURRENCY_ID).setAmount(3.2)).build())
        );
    }

    @Test
    public void test_Process_Refund() {
        when(transactionDao.load(TEST_TRANSACTION_ID)).thenReturn(Optional.of(getRefundableTransaction()));
        when(transactionDao.load(AdditionalMatchers.not(eq(TEST_TRANSACTION_ID)))).thenReturn(Optional.of(getRefundedTransaction()));
        TransactionProto.RefundRequest request = TransactionProto.RefundRequest.newBuilder()
                .setTransactionId(TEST_TRANSACTION_ID)
                .setReason("NA").build();
        handler.handleRefund(getContext(), request);

        verify(transactionDao, times(1)).transactionWrite(any());
        verify(sqsPublisher, times(1)).publish(eq(QUEUE_URL), any());
    }

    @Test
    public void test_Process_Refund_Failed() {
        when(transactionDao.load(TEST_TRANSACTION_ID)).thenReturn(Optional.of(getRefundableTransaction()));
        when(transactionDao.load(AdditionalMatchers.not(eq(TEST_TRANSACTION_ID)))).thenReturn(Optional.of(getRefundedTransaction()));
        doThrow(ConditionalCheckFailedException.class).when(transactionDao).transactionWrite(any());
        TransactionProto.RefundRequest request = TransactionProto.RefundRequest.newBuilder()
                .setTransactionId(TEST_TRANSACTION_ID)
                .setReason("NA").build();
        assertThrows(IllegalStateException.class, () -> handler.handleRefund(getContext(), request));

        verify(sqsPublisher, times(0)).publish(eq(QUEUE_URL), any());
    }

    @ParameterizedTest
    @MethodSource("getIllegalRefundRequestTransacId")
    public void do_Throw_IllegalArgumentException_If_Invalid_Refund_Request(String transactionId) {
        TransactionProto.RefundRequest request = TransactionProto.RefundRequest.newBuilder().setTransactionId(transactionId).build();
        assertThrows(IllegalArgumentException.class, () -> handler.handleRefund(Context.current(), request));
    }

    private static Stream<Arguments> getIllegalRefundRequestTransacId() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("    "),
                Arguments.of("123")
        );
    }

    @ParameterizedTest
    @MethodSource("getIllegalRefundRequestTransac")
    public void do_Throw_IllegalArgumentException_If_Invalid_Refund_Request_Transaction(Transaction transaction) {
        when(transactionDao.load(transaction.getTransactionId())).thenReturn(Optional.of(transaction));
        TransactionProto.RefundRequest request = TransactionProto.RefundRequest.newBuilder()
                .setTransactionId(transaction.getTransactionId()).build();
        assertThrows(IllegalArgumentException.class, () -> handler.handleRefund(getContext(), request));
    }

    private static Stream<Arguments> getIllegalRefundRequestTransac() {
        return Stream.of(
                Arguments.of(Transaction.builder()
                        .status(TransactionStatus.REFUNDED)
                        .type(TransactionType.TRANSFER)
                        .currencyId(CURRENCY_ID)
                        .amount(AMOUNT)
                        .payerId(MY_ID)
                        .recipientId(RECIPIENT_ID)
                        .transactionId(TEST_TRANSACTION_ID)
                        .build()),
                Arguments.of(Transaction.builder()
                        .status(TransactionStatus.COMPLETED)
                        .type(TransactionType.TRANSFER)
                        .currencyId(CURRENCY_ID)
                        .amount(AMOUNT)
                        .payerId(MY_ID)
                        .recipientId(OTHER_ID)
                        .transactionId(TEST_TRANSACTION_ID)
                        .build()),
                Arguments.of(Transaction.builder()
                        .status(TransactionStatus.PENDING)
                        .type(TransactionType.TRANSFER)
                        .currencyId(CURRENCY_ID)
                        .amount(AMOUNT)
                        .payerId(MY_ID)
                        .recipientId(RECIPIENT_ID)
                        .transactionId(TEST_TRANSACTION_ID)
                        .build())
        );
    }

    private Transaction getRefundedTransaction() {
        return Transaction.builder()
                .type(TransactionType.REFUND)
                .currencyId(CURRENCY_ID)
                .amount(AMOUNT)
                .transactionId("123")
                .payerId(MY_ID)
                .recipientId(OTHER_ID)
                .status(TransactionStatus.COMPLETED)
                .createdAt(new Date())
                .lastUpdatedAt(new Date())
                .refundTransacIds(ImmutableList.of(TEST_TRANSACTION_ID))
                .build();
    }

    private Transaction getRefundableTransaction() {
        return Transaction.builder()
                .type(TransactionType.TRANSFER)
                .currencyId(CURRENCY_ID)
                .amount(AMOUNT)
                .transactionId(TEST_TRANSACTION_ID)
                .payerId(OTHER_ID)
                .recipientId(MY_ID)
                .status(TransactionStatus.COMPLETED)
                .createdAt(new Date())
                .lastUpdatedAt(new Date())
                .build();
    }

    private static Context getContext() {
        return Context.current().withValue(InterceptorConfig.USER_NAME_CONTEXT_KEY, MY_ID);
    }

    private static Transaction generateTransaction(String id) {
        return Transaction.builder()
                .transactionId(id)
                .createdAt(new Date())
                .currencyId(CURRENCY_ID)
                .amount(1.3)
                .payerId(MY_ID)
                .recipientId(RECIPIENT_ID)
                .status(TransactionStatus.PENDING)
                .type(TransactionType.TRANSFER)
                .error(TransactionErrorType.NONE)
                .note(NOTE)
                .build();
    }
}
