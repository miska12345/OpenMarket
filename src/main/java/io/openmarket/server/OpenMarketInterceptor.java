package io.openmarket.server;

import com.auth0.jwt.interfaces.Claim;
import io.grpc.*;
import io.openmarket.account.service.CredentialManager;
import io.openmarket.server.config.InterceptorConfig;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.Map;

@Log4j2
public class OpenMarketInterceptor implements io.grpc.ServerInterceptor {
    private final CredentialManager credentialManager;

    @Inject
    public OpenMarketInterceptor(CredentialManager credentialManager) {
        this.credentialManager = credentialManager;
    }


    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadatas,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {
        String token = metadatas.get(InterceptorConfig.AUTHORIZATION_METADATA_KEY);
        Status status;

        if (InterceptorConfig.RPC_WHITE_LIST.contains(call.getMethodDescriptor().getFullMethodName())){
            ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(call, metadatas);
            return new ExceptionHandlingServerCallListener<>(Context.current(), listener, call, metadatas);
        }

        log.info("Handling '{}'", call.getMethodDescriptor().getFullMethodName());

        if (token == null || token.isEmpty()) {
            status = Status.UNAUTHENTICATED.withDescription("Authorization token is missing");
        } else {
            Map<String, Claim> result = credentialManager.getClaims(token);
            if (result.isEmpty()) {
                log.info("Authorization failed with token" + token);
                status = Status.PERMISSION_DENIED.withDescription("Invalid token");
            } else {
                Context ctx = Context.current()
                        .withValue(InterceptorConfig.USER_ID_CONTEXT_KEY, result.get(CredentialManager.CLAIM_USER_ID).asString())
                        .withValue(InterceptorConfig.USER_NAME_CONTEXT_KEY, result.get(CredentialManager.CLAIM_USERNAME).asString());

                ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(call, metadatas);
                return new ExceptionHandlingServerCallListener<>(ctx, listener, call, metadatas);
            }

        }

        call.close(status, metadatas);
        return new ServerCall.Listener<ReqT>() {};
    }


    private class ExceptionHandlingServerCallListener<ReqT, RespT>
        extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private ServerCall<ReqT, RespT> serverCall;
        private Metadata metadata;

        ExceptionHandlingServerCallListener(Context ctx, ServerCall.Listener<ReqT> listener, ServerCall<ReqT, RespT> serverCall,
                                            Metadata metadata) {
            super(listener);
            this.serverCall = serverCall;
            this.metadata = metadata;
        }

        @Override
        public void onHalfClose() {
            try {
                super.onHalfClose();
            } catch (RuntimeException ex) {
                handleException(ex, serverCall, metadata);
                throw ex;
            }
        }

        @Override
        public void onReady() {
            try {
                super.onReady();
            } catch (RuntimeException ex) {
                handleException(ex, serverCall, metadata);
                throw ex;
            }
        }

        private void handleException(Exception exception, ServerCall<ReqT, RespT> serverCall, Metadata metadata) {
            if (exception instanceof IllegalArgumentException) {
                serverCall.close(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()), metadata);
            }
        }
    }
}
