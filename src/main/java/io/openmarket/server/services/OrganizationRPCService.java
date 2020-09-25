package io.openmarket.server.services;
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
                                io.grpc.stub.StreamObserver<OrganizationOuterClass.orgName> responseObserver) {
        responseObserver.onNext(handler.partialUpdateRequest(request));
        responseObserver.onCompleted();
    }
}
