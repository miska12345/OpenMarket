package io.openmarket.stamp.service;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.grpc.Context;
import io.openmarket.event.grpc.EventProto;
import io.openmarket.event.grpc.EventProto.CreateEventRequest;
import io.openmarket.event.grpc.EventProto.CreateEventResult;
import io.openmarket.event.grpc.EventProto.RedeemRequest;
import io.openmarket.event.grpc.EventProto.RedeemResult;
import io.openmarket.server.config.InterceptorConfig;
import io.openmarket.stamp.dao.dynamodb.StampEventDao;
import io.openmarket.stamp.model.EventOwnerType;
import io.openmarket.stamp.model.StampEvent;
import io.openmarket.transaction.grpc.TransactionProto;
import io.openmarket.transaction.service.TransactionServiceHandler;
import io.openmarket.utils.TimeUtils;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.openmarket.config.StampEventConfig.*;

@Log4j2
public class StampEventServiceHandler {
    private static final String ILLEGAL_ARGUMENTS_ERROR_MSG = "The request contains invalid parameters";

    // The event list size to use if the size specified in the request is invalid.
    private static final int DEFAULT_EVENT_LIST_SIZE = 10;

    private final StampEventDao eventDao;
    private final TransactionServiceHandler transactionServiceHandler;


    @Inject
    public StampEventServiceHandler(@NonNull final StampEventDao eventDao,
                                    @NonNull final TransactionServiceHandler transactionServiceHandler) {
        this.eventDao = eventDao;
        this.transactionServiceHandler =transactionServiceHandler;
        log.info("StampEventServiceHandler started");
    }

    public CreateEventResult handleCreateEvent(@NonNull final Context context,
                                               @NonNull final CreateEventRequest request) {
        final String ownerId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(context);
        return createEvent(ownerId, request);
    }

    public EventProto.DeleteEventResult handleDeleteEvent(@NonNull final Context context,
                                                          @NonNull final EventProto.DeleteEventRequest request) {
        final String ownerId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(context);
        return deleteEvent(ownerId, request);
    }

    public RedeemResult handleRedeem(@NonNull final Context context, @NonNull final RedeemRequest request) {
        final String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(context);
        return createRedeem(userId, request);
    }

    public EventProto.GetEventResult handleGetEvent(@NonNull final Context context,
                                                    @NonNull final EventProto.GetEventRequest request) {
        final String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(context);
        return getEvent(userId, request);
    }

    public EventProto.GetEventListResult handleGetEventList(@NonNull final String userID,
                                                      @NonNull final EventProto.GetEventListRequest request) {
        final List<String> eventIds = new ArrayList<>();
        final int maxCount = request.getMaxCount() < 0 ? DEFAULT_EVENT_LIST_SIZE : request.getMaxCount();
        final List<StampEvent> loadResult;
        log.info("User {} requested the event list for {}", userID, request.getOwnerID());
        // TODO: Implement pagination with exclusiveStartKey
        eventDao.getEventIdsByOwner(request.getOwnerID(), null, maxCount, eventIds);
        loadResult = eventIds.isEmpty() ? ImmutableList.of() : eventDao.batchLoad(eventIds);
        return EventProto.GetEventListResult.newBuilder()
                .addAllEvents(loadResult.stream().map(this::convertToGRPCEvent).collect(Collectors.toList()))
                .build();
    }

    public EventProto.GetEventResult getEvent(String userId, EventProto.GetEventRequest request) {
        if (!isGetEventRequestValid(request)) {
            throw new IllegalArgumentException(ILLEGAL_ARGUMENTS_ERROR_MSG);
        }
        final Optional<StampEvent> optEvent = eventDao.load(request.getEventId());
        if (!optEvent.isPresent()) {
            log.warn("User '{}' is trying to get data for a non-existant event '{}'",
                    userId, request.getEventId());
            return EventProto.GetEventResult.newBuilder().setError(EventProto.Error.INVALID_EVENT_ID).build();
        }
        final StampEvent event = optEvent.get();
        log.info("User '{}' inquires for event '{}'", userId, event.getEventId());
        return EventProto.GetEventResult.newBuilder()
                .setError(EventProto.Error.NOTHING)
                .setEvent(convertToGRPCEvent(event))
                .build();
    }

    public RedeemResult createRedeem(@NonNull final String userId, @NonNull final RedeemRequest request) {
        if (!isRedeemRequestValid(request)) {
            throw new IllegalArgumentException(ILLEGAL_ARGUMENTS_ERROR_MSG);
        }
        final String eventId = request.getEventId().trim();
        final Optional<StampEvent> optEvent = eventDao.load(eventId);
        final StampEvent event;
        if (!optEvent.isPresent() || optEvent.get().getExpireAt().before(new Date())) {
            return RedeemResult.newBuilder()
                    .setError(EventProto.Error.INVALID_EVENT_ID)
                    .build();
        }
        event = optEvent.get();
        if (event.getRemainingAmount() <= 0) {
            return RedeemResult.newBuilder()
                    .setError(EventProto.Error.OUT_OF_COINS)
                    .setMessage(event.getMessageOnError())
                    .build();
        }
        try {
            enlistParticipant(userId, event);
        } catch (ConditionalCheckFailedException e) {
            log.error("Failed to enlist participant '{}' for event {}", userId, eventId, e);
            return RedeemResult.newBuilder()
                    .setError(EventProto.Error.ALREADY_REDEEMED)
                    .setMessage(event.getMessageOnError())
                    .build();
        }
        String transactionId = transactionServiceHandler.createPayment(event.getOwnerId(),
                TransactionProto.PaymentRequest.newBuilder()
                .setType(TransactionProto.PaymentRequest.Type.TRANSFER)
                .setRecipientId(userId)
                .setMoneyAmount(TransactionProto.MoneyAmount.newBuilder()
                        .setCurrencyId(event.getCurrencyId())
                        .setAmount(event.getRewardAmount())
                        .build())
                .setNote(event.getMessageOnSuccess())
                .build());
        log.info("Successfully redeemed user '{}' at event {}", userId, request.getEventId());
        return RedeemResult.newBuilder()
                .setError(EventProto.Error.NOTHING)
                .setMessage(event.getMessageOnSuccess())
                .setTransactionId(transactionId)
                .setEvent(convertToGRPCEvent(event))
                .build();
    }

    public CreateEventResult createEvent(@NonNull final String ownerId, @NonNull final CreateEventRequest request) {
        if (!isCreateEventRequestValid(request)) {
            throw new IllegalArgumentException(ILLEGAL_ARGUMENTS_ERROR_MSG);
        }

        // TODO: Optimize
        final StampEvent event = StampEvent.builder()
                .ownerId(ownerId)
                .name(request.getName())
                .type(EventOwnerType.USER)
                .currencyId(request.getCurrency())
                .totalAmount(request.getTotalAmount())
                .rewardAmount(request.getRewardAmount())
                .remainingAmount(request.getTotalAmount())
                .expireAt(TimeUtils.parseDate(request.getExpiresAt()))
                .build();
        eventDao.save(event);
        return CreateEventResult.newBuilder().setEventId(event.getEventId()).build();
    }

    public EventProto.DeleteEventResult deleteEvent(@NonNull final String userId,
                                                    @NonNull final EventProto.DeleteEventRequest request) {
        if (!isDeleteRequestValid(request)) {
            throw new IllegalArgumentException(ILLEGAL_ARGUMENTS_ERROR_MSG);
        }
        final Optional<StampEvent> event = eventDao.load(request.getEventId());
        EventProto.Error error = EventProto.Error.INVALID_EVENT_ID;
        if (event.isPresent()) {
            if (event.get().getOwnerId().equals(userId)) {
                eventDao.delete(event.get().getEventId());
                error = EventProto.Error.NOTHING;
            } else {
                error = EventProto.Error.UNAUTHORIZED;
            }
        }
        return EventProto.DeleteEventResult.newBuilder().setError(error).build();
    }

    private void enlistParticipant(final String userId, final StampEvent event) {
        final UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName(EVENT_DDB_TABLE_NAME)
                .withKey(ImmutableMap.of(EVENT_DDB_ATTRIBUTE_ID, new AttributeValue(event.getEventId())))
                .withUpdateExpression("ADD #par :userSS SET #rem = #rem - :awrd")
                .withConditionExpression("not(contains(#par, :userId)) AND #rem >= :awrd")
                .withExpressionAttributeNames(ImmutableMap.of(
                        "#par", EVENT_DDB_ATTRIBUTE_PARTICIPANTS,
                        "#rem", EVENT_DDB_ATTRIBUTE_REMAINING_AMOUNT
                ))
                .withExpressionAttributeValues(ImmutableMap.of(
                        ":userSS", new AttributeValue().withSS(userId),
                        ":userId", new AttributeValue(userId),
                        ":awrd", new AttributeValue().withN(String.valueOf(event.getRewardAmount()))
                ));
        eventDao.update(updateRequest);
    }

    private EventProto.Event convertToGRPCEvent(final StampEvent event) {
        return EventProto.Event.newBuilder()
                .setEventId(event.getEventId())
                .setName(event.getName())
                .setCreatedAt(event.getCreatedAt().toString())
                .setCurrency(event.getCurrencyId())
                .setExpiresAt(TimeUtils.formatDate(event.getExpireAt()))
                .setOwner(event.getOwnerId())
                .setRemainingAmount(event.getRemainingAmount())
                .setTotalAmount(event.getTotalAmount())
                .setRewardAmount(event.getRewardAmount())
                .setType(EventProto.OwnerType.valueOf(event.getType().toString()))
                .setSuccessMessage(event.getMessageOnSuccess())
                .setErrorMessage(event.getMessageOnError())
                .build();
    }

    private static boolean isRedeemRequestValid(final RedeemRequest request) {
        return !request.getEventId().trim().isEmpty();
    }

    private static boolean isDeleteRequestValid(final EventProto.DeleteEventRequest request) {
        return !request.getEventId().trim().isEmpty();
    }

    private static boolean isCreateEventRequestValid(final CreateEventRequest request) {
        final Date expirationDate = TimeUtils.parseDate(request.getExpiresAt());
        final Date today = new Date();
        if (expirationDate.before(today)) {
            throw new IllegalArgumentException("Expiration date is invalid");
        }
        return !request.getCurrency().trim().isEmpty()
                && request.getRewardAmount() > 0
                && request.getTotalAmount() > 0
                && request.getRewardAmount() <= request.getTotalAmount()
                && expirationDate.after(today);
    }

    private static boolean isGetEventRequestValid(final EventProto.GetEventRequest request) {
        return !request.getEventId().isEmpty();
    }
}
