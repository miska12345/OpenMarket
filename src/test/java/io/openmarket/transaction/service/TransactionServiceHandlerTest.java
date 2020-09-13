package io.openmarket.transaction.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
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
import org.mockito.stubbing.Answer;

import java.util.*;
import java.util.stream.Stream;

import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_ERROR_TYPE;
import static io.openmarket.config.TransactionConfig.TRANSACTION_INITIAL_STATUS;
import static io.openmarket.transaction.grpc.TransactionProto.QueryRequest.QueryType.TRANSACTION_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class TransactionServiceHandlerTest {
    private static final String CURRENCY_ID = "100";
    private static final String PAYER_ID = "001";
    private static final String RECIPIENT_ID = "002";
    private static final String NOTE = "Hello";

    private TransactionDao transactionDao;
    private TransactionServiceHandler handler;

    @BeforeEach
    public void setup() {
        this.transactionDao = mock(TransactionDao.class);
        this.handler = new TransactionServiceHandler(transactionDao);
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
        assertEquals(mockTransaction.getCurrencyId(), item.getCurrencyId());
        assertEquals(mockTransaction.getAmount(), item.getAmount());
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
                Arguments.of(TransactionProto.QueryRequest.QueryType.PAYER_ID, PAYER_ID, 3, true),
                Arguments.of(TransactionProto.QueryRequest.QueryType.PAYER_ID, PAYER_ID, 0, false)
        );
    }

    @Test
    public void check_Transfer_DB_Entry() {
        TransactionProto.PaymentRequest request = TransactionProto.PaymentRequest.newBuilder()
                .setPayerId(PAYER_ID)
                .setRecipientId(RECIPIENT_ID)
                .setCurrencyId(CURRENCY_ID)
                .setAmount(3.13)
                .setNote("")
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        TransactionProto.PaymentResult result = handler.handlePayment(request);

        verify(transactionDao, times(1)).save(argThat(
                a -> a.getTransactionId().equals(result.getTransactionId())
                        && a.getPayerId().equals(request.getPayerId())
                        && a.getRecipientId().equals(request.getRecipientId())
                        && a.getStatus().equals(TRANSACTION_INITIAL_STATUS)
                        && a.getError().equals(TRANSACTION_INITIAL_ERROR_TYPE)
                        && a.getCurrencyId().equals(request.getCurrencyId())
                        && a.getAmount().equals(request.getAmount())
                        && a.getNote().equals(request.getNote())
                )
        );
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
                .setPayerId(PAYER_ID)
                .setRecipientId(PAYER_ID)
                .setCurrencyId(CURRENCY_ID)
                .setAmount(3.13)
                .setNote("")
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .build();
        assertThrows(IllegalArgumentException.class, () ->  handler.handlePayment(request));
        verify(transactionDao, times(0)).save(any());
    }

    @Test
    public void test_isTransferRequestValid() {
        assertFalse(handler.isTransferRequestValid(TransactionProto.PaymentRequest.newBuilder().setPayerId("").build()));
        assertFalse(handler.isTransferRequestValid(TransactionProto.PaymentRequest.newBuilder().setPayerId(PAYER_ID).setRecipientId("").build()));
        assertFalse(handler.isTransferRequestValid(TransactionProto.PaymentRequest.newBuilder().setPayerId(PAYER_ID).setRecipientId(RECIPIENT_ID).setCurrencyId("").build()));
        assertFalse(handler.isTransferRequestValid(TransactionProto.PaymentRequest.newBuilder().setPayerId(PAYER_ID).setRecipientId(RECIPIENT_ID).setCurrencyId(CURRENCY_ID).setAmount(0).build()));
        assertFalse(handler.isTransferRequestValid(TransactionProto.PaymentRequest.newBuilder().setPayerId(PAYER_ID).setRecipientId(RECIPIENT_ID).setCurrencyId(CURRENCY_ID).setAmount(-1).build()));
        assertFalse(handler.isTransferRequestValid(TransactionProto.PaymentRequest.newBuilder().setPayerId(PAYER_ID).setRecipientId(PAYER_ID).setCurrencyId(CURRENCY_ID).setAmount(1.3).build()));
    }

    private Transaction generateTransaction(String id) {
        return Transaction.builder()
                .transactionId(id)
                .createdAt(new Date())
                .currencyId(CURRENCY_ID)
                .amount(1.3)
                .payerId(PAYER_ID)
                .recipientId(RECIPIENT_ID)
                .status(TransactionStatus.PENDING)
                .type(TransactionType.TRANSFER)
                .error(TransactionErrorType.NONE)
                .note(NOTE)
                .build();
    }
}
