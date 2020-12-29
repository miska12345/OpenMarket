package io.openmarket.server.services;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.openmarket.marketplace.MarketPlaceServiceHandler;
import io.openmarket.marketplace.grpc.MarketPlaceGrpc;
import io.openmarket.marketplace.grpc.MarketPlaceProto;
import io.openmarket.server.config.InterceptorConfig;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.inject.Inject;

@Log4j2
public class MarketPlaceRPCService extends MarketPlaceGrpc.MarketPlaceImplBase {
    private final MarketPlaceServiceHandler marketPlaceServiceHandler;

    @Inject
    public MarketPlaceRPCService(@Nonnull final MarketPlaceServiceHandler marketPlaceServiceHandler) {
        this.marketPlaceServiceHandler = marketPlaceServiceHandler;
    }

    @Override
    public void checkout (@Nonnull final MarketPlaceProto.CheckOutRequest request,
                                @Nonnull final StreamObserver<MarketPlaceProto.CheckOutResult> observer) {
        final String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(Context.current());
        log.info("CheckOut invoked by user {}", userId);
        observer.onNext(this.marketPlaceServiceHandler.checkout(userId, request));
        observer.onCompleted();
    }

    @Override
    public void getAllOrders (@Nonnull final MarketPlaceProto.GetAllOrdersRequest request,
                          @Nonnull final StreamObserver<MarketPlaceProto.GetAllOrdersResult> observer) {
        final String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(Context.current());
        log.info("Get order invoked by user {}", userId);
        observer.onNext(this.marketPlaceServiceHandler.getOrders(userId, request));
        observer.onCompleted();
    }
}
