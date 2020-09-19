package io.openmarket.server;

import com.auth0.jwt.interfaces.Claim;
import io.grpc.Context;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.openmarket.account.service.CredentialManager;
import io.openmarket.server.config.InterceptorConfig;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

import static io.openmarket.config.EnvironmentConfig.ENV_VAR_RPC_USE_VALIDATION;

@Log4j2
public class OpenMarketInterceptor implements io.grpc.ServerInterceptor {
    private final boolean useValidation;
    private final CredentialManager credentialManager;

    @Inject
    public OpenMarketInterceptor(@Named(ENV_VAR_RPC_USE_VALIDATION) final boolean useValidation,
                                 @NonNull final CredentialManager credentialManager) {
        this.useValidation = useValidation;
        this.credentialManager = credentialManager;
        if (!useValidation) {
            log.warn("Server is not enforcing validation for tokens! If this is not what you want, update and restart now.");
        }
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata metadatas,
            ServerCallHandler<ReqT, RespT> serverCallHandler) {
        final String token = metadatas.get(InterceptorConfig.AUTHORIZATION_METADATA_KEY);
        final Status status;

        if (InterceptorConfig.RPC_WHITE_LIST.contains(call.getMethodDescriptor().getFullMethodName())){
            ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(call, metadatas);
            return new ExceptionHandlingServerCallListener<>(Context.current(), listener, call, metadatas);
        }

        log.info("Handling '{}'", call.getMethodDescriptor().getFullMethodName());

        if (token == null || token.isEmpty()) {
            status = Status.UNAUTHENTICATED.withDescription("Authorization token is missing");
        } else {
            final Map<String, Claim> result = useValidation ?
                    credentialManager.getClaims(token) : credentialManager.getClaimsUnverified(token);
            if (result.isEmpty()) {
                log.info("Authorization failed with token " + token);
                status = Status.PERMISSION_DENIED.withDescription("Invalid token");
            } else {
                log.info("Authenticated user with name '{}'", result.get(CredentialManager.CLAIM_USERNAME).asString());
                final Context ctx = Context.current()
                        .withValue(InterceptorConfig.USER_ID_CONTEXT_KEY, result.get(CredentialManager.CLAIM_USER_ID).asString())
                        .withValue(InterceptorConfig.USER_NAME_CONTEXT_KEY, result.get(CredentialManager.CLAIM_USERNAME).asString());
                final ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(call, metadatas);
                return new ExceptionHandlingServerCallListener<>(ctx, listener, call, metadatas);
            }
        }

        call.close(status, metadatas);
        return new ServerCall.Listener<ReqT>() {};
    }


    private static class ExceptionHandlingServerCallListener<ReqT, RespT>
        extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {

        private final Context ctx;
        private final ServerCall<ReqT, RespT> serverCall;
        private final Metadata metadata;

        ExceptionHandlingServerCallListener(Context ctx, ServerCall.Listener<ReqT> listener, ServerCall<ReqT, RespT> serverCall,
                                            Metadata metadata) {
            super(listener);
            this.ctx = ctx;
            this.serverCall = serverCall;
            this.metadata = metadata;
        }

        @Override
        public void onMessage(final ReqT message) {
            final Context previous = this.ctx.attach();
            try {
                super.onMessage(message);
            } finally {
                this.ctx.detach(previous);
            }
        }

        @Override
        public void onHalfClose() {
            final Context previous = this.ctx.attach();
            try {
                super.onHalfClose();
            } catch (RuntimeException ex) {
                handleException(ex, serverCall, metadata);
                throw ex;
            } finally {
                ctx.detach(previous);
            }
        }

        @Override
        public void onReady() {
            final Context previous = ctx.attach();
            try {
                super.onReady();
            } catch (RuntimeException ex) {
                handleException(ex, serverCall, metadata);
                throw ex;
            } finally {
                ctx.detach(previous);
            }
        }

        private void handleException(Exception exception, ServerCall<ReqT, RespT> serverCall, Metadata metadata) {
            if (exception instanceof IllegalArgumentException) {
                serverCall.close(Status.INVALID_ARGUMENT.withDescription(exception.getMessage()), metadata);
            }
        }
    }
}
