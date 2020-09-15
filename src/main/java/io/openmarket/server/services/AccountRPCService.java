package io.openmarket.server.services;

import io.grpc.stub.StreamObserver;
import io.openmarket.account.grpc.AccountGrpc;
import io.openmarket.account.grpc.AccountService.LoginRequest;
import io.openmarket.account.grpc.AccountService.LoginResult;
import io.openmarket.account.grpc.AccountService.RegistrationRequest;
import io.openmarket.account.grpc.AccountService.RegistrationResult;
import io.openmarket.account.service.AccountServiceHandler;

import javax.inject.Inject;

public class AccountRPCService extends AccountGrpc.AccountImplBase {

    private final AccountServiceHandler accountHandler;

    @Inject
    public AccountRPCService(AccountServiceHandler handler) {
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
