package io.openmarket.server.services;

import io.openmarket.account.grpc.AccountGrpc;
import io.openmarket.account.grpc.AccountService.*;
import io.grpc.stub.StreamObserver;
import io.openmarket.account.service.AccountServiceHandler;
import io.openmarket.account.service.CredentialManager;

import javax.inject.Inject;

public class AccountRPCServiceImpl extends AccountGrpc.AccountImplBase {

    private final AccountServiceHandler accountHandler;

    @Inject
    public AccountRPCServiceImpl(AccountServiceHandler handler) {
        accountHandler = handler;
    }

    @Override
    public void handleLogin(LoginRequest request, StreamObserver<LoginResult> responseObserver) {
        LoginResult result = this.accountHandler.login(request);
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }

    @Override
    public void handleRegister(RegistrationRequest request, StreamObserver<RegistrationResult> responseObserver) {
        RegistrationResult result = this.accountHandler.register(request);
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }
}