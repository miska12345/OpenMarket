package io.openmarket.server.services;

import io.grpc.stub.StreamObserver;
import io.openmarket.newsfeed.NewsFeedServiceHandler;
import io.openmarket.newsfeed.grpc.NewsFeedGrpc;
import io.openmarket.newsfeed.grpc.NewsFeedProto;

import javax.annotation.Nonnull;
import javax.inject.Inject;

public class NewsFeedRPCService extends NewsFeedGrpc.NewsFeedImplBase {
    private NewsFeedServiceHandler newsFeedServiceHandler;

    @Inject
    public NewsFeedRPCService(@Nonnull NewsFeedServiceHandler handler){
        this.newsFeedServiceHandler = handler;
    }
    @Override
    public void getTopDeals(NewsFeedProto.TopDealsRequest request, StreamObserver<NewsFeedProto.TopDealsResult> responseObserver) {
        NewsFeedProto.TopDealsResult result = newsFeedServiceHandler.getTopDeals(request);
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }

    @Override
    public void getPosters(NewsFeedProto.GetPosterRequest request, StreamObserver<NewsFeedProto.GetPosterResult> responseObserver) {
        super.getPosters(request, responseObserver);

    }

    @Override
    public void getRecommendItems(NewsFeedProto.RecommendItemRequest request, StreamObserver<NewsFeedProto.RecommendItemResult> responseObserver) {
        super.getRecommendItems(request, responseObserver);
    }

    @Override
    public void getRecommendedEvents(NewsFeedProto.RecommendEventsRequest request, StreamObserver<NewsFeedProto.RecommendEventsResult> responseObserver) {
        super.getRecommendedEvents(request, responseObserver);
    }

    @Override
    public void getRecommendedOrganization(NewsFeedProto.RecommendOrganizationRequest request, StreamObserver<NewsFeedProto.RecommendOrganizationResult> responseObserver) {
        super.getRecommendedOrganization(request, responseObserver);
    }
}
