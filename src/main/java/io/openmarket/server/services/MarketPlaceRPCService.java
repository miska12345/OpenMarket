package io.openmarket.server.services;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;
import io.openmarket.marketplace.MarketPlaceServiceHandler;
import io.openmarket.marketplace.grpc.MarketPlaceGrpc;
import io.openmarket.marketplace.grpc.MarketPlaceProto.*;
import io.openmarket.server.config.InterceptorConfig;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class MarketPlaceRPCService extends MarketPlaceGrpc.MarketPlaceImplBase {
    private MarketPlaceServiceHandler marketPlaceServiceHandler;

    @Inject
    public MarketPlaceRPCService(@Nonnull final MarketPlaceServiceHandler marketPlaceServiceHandler) {
        this.marketPlaceServiceHandler = marketPlaceServiceHandler;
    }
//
//    @Override
//    public void handleAddItems (@Nonnull final AddItemRequest request,
//                                @Nonnull final StreamObserver<AddItemResult> observer) {
//        observer.onNext(this.marketPlaceServiceHandler.addItem(request));
//        observer.onCompleted();
//    }
//
//    @Override
//    public void handleGetOrgItems (@Nonnull final GetOrgItemsRequest request,
//                                   @Nonnull final StreamObserver<GetOrgItemsResult> observer) {
//        observer.onNext(this.marketPlaceServiceHandler.getListingByOrgId(request));
//        observer.onCompleted();
//    }
//
//    @Override
//    public void handleCheckout (@Nonnull final CheckOutRequest request,
//                                @Nonnull final StreamObserver<CheckOutResult> observer) {
//        String userId = InterceptorConfig.USER_NAME_CONTEXT_KEY.get(Context.current());
//        observer.onNext(this.marketPlaceServiceHandler.checkout(userId, request));
//        observer.onCompleted();
//    }
//
//    @Override
//    public void handleGetSimilarItems (@Nonnull final GetSimilarItemsRequest request,
//                                       @Nonnull final StreamObserver<GetSimilarItemsResult> observer) {
//        observer.onNext(this.marketPlaceServiceHandler.getSimilarItem(request));
//        observer.onCompleted();
//    }

}
