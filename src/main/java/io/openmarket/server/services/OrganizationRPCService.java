package io.openmarket.server.services;
import io.grpc.stub.StreamObserver;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.grpc.OrganizationGrpc;
import io.openmarket.organization.grpc.OrganizationOuterClass;
import lombok.NonNull;

import javax.inject.Inject;

public class OrganizationRPCService extends OrganizationGrpc.OrganizationImplBase {
    private final OrgServiceHandler handler;

    @Inject
    public OrganizationRPCService(@NonNull final OrgServiceHandler handler) {
        this.handler = handler;
    }

    @Override
    public void updateFollower(OrganizationOuterClass.UpdateFollowerRequest request, StreamObserver<OrganizationOuterClass.UpdateFollowerResult> responseObserver) {
        responseObserver.onNext(handler.updateFollower(request));
        responseObserver.onCompleted();
    }

    @Override
    public void getFollowers(OrganizationOuterClass.GetFollowerRequest request, StreamObserver<OrganizationOuterClass.GetFollowerResult> responseObserver) {
        responseObserver.onNext(handler.getFollowerIds(request));
        responseObserver.onCompleted();
    }

    @Override
    public void isUserFollowing(OrganizationOuterClass.IsUserFollowingRequest request, StreamObserver<OrganizationOuterClass.IsUserFollowingResult> responseObserver) {
        responseObserver.onNext(handler.isUserFollowing(request));
        responseObserver.onCompleted();
    }

    @Override
    public void getOrganization(OrganizationOuterClass.orgName request,
                                io.grpc.stub.StreamObserver<OrganizationOuterClass.orgMetadata> responseObserver) {
        responseObserver.onNext(handler.getOrgRequest(request));
        responseObserver.onCompleted();
    }

    @Override
    public void addOrganization(OrganizationOuterClass.orgMetadata request,
                                io.grpc.stub.StreamObserver<OrganizationOuterClass.orgName> responseObserver) {
        responseObserver.onNext(handler.addOrgRquest(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateOrganization(OrganizationOuterClass.orgMetadata request,
                                io.grpc.stub.StreamObserver<OrganizationOuterClass.OrgUpdateResult> responseObserver) {
        responseObserver.onNext(handler.partialUpdateRequest(request));
        responseObserver.onCompleted();
    }
}
