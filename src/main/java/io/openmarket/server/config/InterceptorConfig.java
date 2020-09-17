package io.openmarket.server.config;

import com.google.common.collect.ImmutableSet;
import io.grpc.Context;
import io.grpc.Metadata;

import java.util.Set;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class InterceptorConfig {
    /**
     * The metadata key for authorization credential
     */
    public static final Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of("AuthToken", ASCII_STRING_MARSHALLER);

    /**
     *The context key for the user id
     */
    public static final Context.Key<String> USER_ID_CONTEXT_KEY = Context.key("UserId");

    /**
     * The context key for user name
     */
    public static final Context.Key<String> USER_NAME_CONTEXT_KEY = Context.key("Username");

    /**
     * The white list for method that doesn't need authentication
     */
    public static final Set<String> RPC_WHITE_LIST = ImmutableSet.of("Account/handleRegister", "Account/handleLogin");

}
