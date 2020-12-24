package io.openmarket.server.services;

import io.grpc.stub.StreamObserver;
import io.openmarket.account.grpc.AccountGrpc;
import io.openmarket.account.grpc.AccountService.*;
import io.openmarket.account.service.AccountServiceHandler;
import io.openmarket.server.config.InterceptorConfig;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

@Log4j2
public class AccountRPCService extends AccountGrpc.AccountImplBase {

    private final AccountServiceHandler accountHandler;

    @Inject
    public AccountRPCService(AccountServiceHandler handler) {
        accountHandler = handler;
    }

    @Override
    public void handleLogin(LoginRequest request, StreamObserver<LoginResult> responseObserver) {
        String userID = InterceptorConfig.USER_NAME_CONTEXT_KEY.get();
        LoginResult result = this.accountHandler.login(request);
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }


    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResult> responseObserver) {
        responseObserver.onNext(accountHandler.getUser(request));
        responseObserver.onCompleted();
    }

    @Override
    public void handleRegister(RegistrationRequest request, StreamObserver<RegistrationResult> responseObserver) {
        RegistrationResult result = this.accountHandler.register(request);
        responseObserver.onNext(result);
        responseObserver.onCompleted();
    }

}
