package io.openmarket.server.services;

import io.openmarket.organization.OrgServiceHandler;
import lombok.NonNull;
import organization.OrganizationGrpc;

import javax.inject.Inject;

public class OrganizationRPCService extends OrganizationGrpc.OrganizationImplBase {
    private final OrgServiceHandler handler;

    @Inject
    public OrganizationRPCService(@NonNull final OrgServiceHandler handler) {
        this.handler = handler;
    }

    @Override
    public void getOrganization(organization.OrganizationOuterClass.orgName request,
                                io.grpc.stub.StreamObserver<organization.OrganizationOuterClass.orgMetadata> responseObserver) {
        responseObserver.onNext(handler.getOrgRequest(request));
        responseObserver.onCompleted();
    }

    @Override
    public void addOrganization(organization.OrganizationOuterClass.orgMetadata request,
                                io.grpc.stub.StreamObserver<organization.OrganizationOuterClass.orgName> responseObserver) {
        responseObserver.onNext(handler.addOrgRquest(request));
        responseObserver.onCompleted();
    }

    @Override
    public void updateOrganization(organization.OrganizationOuterClass.orgMetadata request,
                                io.grpc.stub.StreamObserver<organization.OrganizationOuterClass.orgName> responseObserver) {
        responseObserver.onNext(handler.partialUpdateRequest(request));
        responseObserver.onCompleted();
    }
}
