package io.openmarket.event.service;

import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import io.openmarket.event.grpc.EventProto;
import io.openmarket.stamp.dao.dynamodb.StampEventDao;
import io.openmarket.stamp.model.EventOwnerType;
import io.openmarket.stamp.model.StampEvent;
import io.openmarket.stamp.service.StampEventServiceHandler;
import io.openmarket.transaction.service.TransactionServiceHandler;
import io.openmarket.utils.TimeUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class StampEventHandlerTest {
    private static final String INVALID_EVENT_ID = "123";
    private static final String VALID_EVENT_ID = "666";
    private static final String USER_ID = "miska";
    private static final String OWNER_ID = "foo";
    private static final Double TOTAL_AMOUNT = 1000.0;
    private static final Double REWARD_AMOUNT = 1.0;
    private static final String CURRENCY_ID = "aaa";
    private static final String SUCCESS_MSG = "Success";
    private static final String ERROR_MSG = "Error";
    private StampEventDao eventDao;
    private TransactionServiceHandler transactionServiceHandler;
    private StampEventServiceHandler stampEventServiceHandler;

    @BeforeEach
    public void setup() {
        this.eventDao = mock(StampEventDao.class);
        this.transactionServiceHandler = mock(TransactionServiceHandler.class);
        this.stampEventServiceHandler = new StampEventServiceHandler(eventDao, transactionServiceHandler);
    }

    @Test
    public void when_EventID_is_Invalid_Then_Return_Invalid_Event_Status() {
        when(eventDao.load(INVALID_EVENT_ID)).thenReturn(Optional.empty());
        EventProto.RedeemResult result = stampEventServiceHandler.createRedeem(USER_ID,
                EventProto.RedeemRequest.newBuilder().setEventId(INVALID_EVENT_ID).build());
        assertEquals(EventProto.Error.INVALID_EVENT_ID, result.getError());
    }

    @Test
    public void when_EventID_Valid_Then_Redeem() {
        String transactionId = "123";
        StampEvent event = getStampEvent(100.0);
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(event));
        when(transactionServiceHandler.createPayment(anyString(), any())).thenReturn(transactionId);
        EventProto.RedeemResult result = stampEventServiceHandler
                .createRedeem(USER_ID, EventProto.RedeemRequest.newBuilder().setEventId(event.getEventId()).build());
        verify(transactionServiceHandler, times(1)).createPayment(eq(OWNER_ID),
                argThat(a -> a.getRecipientId().equals(USER_ID)
                        && a.getMoneyAmount().getCurrencyId().equals(CURRENCY_ID)
                        && a.getMoneyAmount().getAmount() == REWARD_AMOUNT
                ));
        assertEquals(EventProto.Error.NOTHING, result.getError());
        assertEquals(event.getMessageOnSuccess(), result.getMessage());
        assertEquals(transactionId, result.getTransactionId());
        assertEquals(event.getEventId(), result.getEvent().getEventId());
    }

    @Test
    public void when_Event_Expired_Then_Return_Invalid_Status() {
        StampEvent event = getStampEvent(100.0);
        event.setExpireAt(TimeUtils.getDateBefore(new Date(), 10));
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(event));
        EventProto.RedeemResult result = stampEventServiceHandler
                .createRedeem(USER_ID, EventProto.RedeemRequest.newBuilder().setEventId(event.getEventId()).build());
        assertEquals(EventProto.Error.INVALID_EVENT_ID, result.getError());
    }

    @Test
    public void when_Already_Redeemed_Then_Return_ALREADY_REDEEMED_Status() {
        StampEvent event = getStampEvent(100.0);
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(event));
        doThrow(ConditionalCheckFailedException.class).when(eventDao).update(any());
        EventProto.RedeemResult result = stampEventServiceHandler
                .createRedeem(USER_ID, EventProto.RedeemRequest.newBuilder().setEventId(event.getEventId()).build());
        assertEquals(EventProto.Error.ALREADY_REDEEMED, result.getError());
        assertEquals(event.getMessageOnError(), result.getMessage());
    }

    @Test
    public void when_Out_of_Coins_Then_Return_OUT_OF_COINS_Status() {
        StampEvent event = getStampEvent(0.0);
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(event));
        EventProto.RedeemResult result = stampEventServiceHandler
                .createRedeem(USER_ID, EventProto.RedeemRequest.newBuilder().setEventId(event.getEventId()).build());
        assertEquals(EventProto.Error.OUT_OF_COINS, result.getError());
        assertEquals(event.getMessageOnError(), result.getMessage());
    }

    @Test
    public void when_Redeem_With_Invalid_Request_Then_Throw_IllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> stampEventServiceHandler.createRedeem(USER_ID, EventProto.RedeemRequest.newBuilder()
                .setEventId("")
                .build()));
    }

    @Test
    public void test_Create_Event() {
        stampEventServiceHandler.createEvent(USER_ID, EventProto.CreateEventRequest.newBuilder()
                .setRewardAmount(REWARD_AMOUNT)
                .setTotalAmount(TOTAL_AMOUNT)
                .setCurrency(CURRENCY_ID)
                .setExpiresAt("2099-07-01T12:00:00Z")
                .build()
        );

        verify(eventDao, times(1)).save(any());
    }

    @ParameterizedTest
    @MethodSource("getInvalidCreateEventRequest")
    public void test_Create_Event_Invalid_Request(EventProto.CreateEventRequest request) {
        assertThrows(IllegalArgumentException.class,
                () -> stampEventServiceHandler.createEvent(USER_ID, request));
    }

    private static Stream<Arguments> getInvalidCreateEventRequest() {
        return Stream.of(
                Arguments.of(EventProto.CreateEventRequest.newBuilder()
                        .setRewardAmount(TOTAL_AMOUNT * 2)
                        .setTotalAmount(TOTAL_AMOUNT)
                        .setCurrency(CURRENCY_ID)
                        .setExpiresAt("2099-07-01T12:00:00Z")
                        .build()),
                Arguments.of(EventProto.CreateEventRequest.newBuilder()
                        .setRewardAmount(-9)
                        .setTotalAmount(TOTAL_AMOUNT)
                        .setCurrency(CURRENCY_ID)
                        .setExpiresAt("2099-07-01T12:00:00Z")
                        .build()),
                Arguments.of(EventProto.CreateEventRequest.newBuilder()
                        .setRewardAmount(REWARD_AMOUNT)
                        .setTotalAmount(-1)
                        .setCurrency(CURRENCY_ID)
                        .setExpiresAt("2099-07-01T12:00:00Z")
                        .build()),
                Arguments.of(EventProto.CreateEventRequest.newBuilder()
                        .setRewardAmount(REWARD_AMOUNT)
                        .setTotalAmount(TOTAL_AMOUNT)
                        .setCurrency(CURRENCY_ID)
                        .setExpiresAt("1998-01-01T12:00:00Z")
                        .build())
        );
    }

    @Test
    public void test_Delete_Event_As_Owner() {
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(getStampEvent(10.0)));
        EventProto.DeleteEventResult result = stampEventServiceHandler.deleteEvent(OWNER_ID,
                EventProto.DeleteEventRequest.newBuilder().setEventId(VALID_EVENT_ID).build());
        assertEquals(EventProto.Error.NOTHING, result.getError());
    }

    @Test
    public void test_Delete_Event_As_Not_Owner() {
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(getStampEvent(10.0)));
        EventProto.DeleteEventResult result = stampEventServiceHandler.deleteEvent(USER_ID,
                EventProto.DeleteEventRequest.newBuilder().setEventId(VALID_EVENT_ID).build());
        assertEquals(EventProto.Error.UNAUTHORIZED, result.getError());
    }

    @Test
    public void test_Delete_No_Such_Event() {
        when(eventDao.load(INVALID_EVENT_ID)).thenReturn(Optional.empty());
        EventProto.DeleteEventResult result = stampEventServiceHandler.deleteEvent(OWNER_ID,
                EventProto.DeleteEventRequest.newBuilder().setEventId(VALID_EVENT_ID).build());
        assertEquals(EventProto.Error.INVALID_EVENT_ID, result.getError());
    }

    @Test
    public void test_Delete_With_Bad_Request() {
        assertThrows(IllegalArgumentException.class,
                () -> stampEventServiceHandler.deleteEvent(OWNER_ID, EventProto.DeleteEventRequest.newBuilder().setEventId("").build()));
    }

    @Test
    public void test_Get_Event_When_Exists() {
        StampEvent event = getStampEvent(10.0);
        when(eventDao.load(VALID_EVENT_ID)).thenReturn(Optional.of(event));
        EventProto.GetEventResult result = stampEventServiceHandler.getEvent(USER_ID, EventProto.GetEventRequest.newBuilder()
                .setEventId(VALID_EVENT_ID)
                .build());
        assertEquals(EventProto.Error.NOTHING, result.getError());
        assertEquals(event.getEventId(), result.getEvent().getEventId());
    }

    @Test
    public void test_Get_Event_When_Not_Exists() {
        when(eventDao.load(INVALID_EVENT_ID)).thenReturn(Optional.empty());
        EventProto.GetEventResult result = stampEventServiceHandler.getEvent(USER_ID, EventProto.GetEventRequest.newBuilder()
                .setEventId(INVALID_EVENT_ID)
                .build());
        assertEquals(EventProto.Error.INVALID_EVENT_ID, result.getError());
    }

    @Test
    public void test_Get_Event_Invalid_Request() {
        assertThrows(IllegalArgumentException.class,
                () -> stampEventServiceHandler.getEvent(USER_ID, EventProto.GetEventRequest.newBuilder()
                .setEventId("")
                .build()));
    }

    private StampEvent getStampEvent(Double remainingAmount) {
        final Date today = new Date();
        return StampEvent.builder()
                .type(EventOwnerType.USER)
                .ownerId(OWNER_ID)
                .remainingAmount(remainingAmount)
                .totalAmount(TOTAL_AMOUNT)
                .rewardAmount(REWARD_AMOUNT)
                .createdAt(today)
                .expireAt(TimeUtils.getDateAfter(today, 10))
                .currencyId(CURRENCY_ID)
                .eventId(VALID_EVENT_ID)
                .messageOnError(SUCCESS_MSG)
                .messageOnError(ERROR_MSG)
                .build();
    }
}
