package io.openmarket.server;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import io.grpc.Status;
import io.openmarket.config.GlobalConfig;

import java.util.concurrent.Executor;

import static io.grpc.Metadata.ASCII_STRING_MARSHALLER;

public class MyCredential extends CallCredentials {
    @Override
    public void applyRequestMetadata(RequestInfo requestInfo, Executor appExecutor, MetadataApplier applier) {
        try {
            Metadata meta = new Metadata();
            Metadata.Key<String> AUTHORIZATION_METADATA_KEY = Metadata.Key.of(GlobalConfig.ATTR_AUTHENTICATION_TOKEN,
                    ASCII_STRING_MARSHALLER);
            meta.put(AUTHORIZATION_METADATA_KEY, "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJVc2VybmFtZSI6InVzZXIiLCJVc2VySWQiOiIxMjMiLCJpc3MiOiJPcGVuTWFya2V0IiwiZXhwIjoxNjAwNDUxNTcwLCJpYXQiOjE2MDAzNjUxNzB9.nkoqKk2YPv0AU5rm3nALyhg3J67Y-9d-PD-rfTOcvgs");
            applier.apply(meta);
        } catch (Throwable e) {
            applier.fail(Status.UNAUTHENTICATED.withCause(e));
        }
    }

    @Override
    public void thisUsesUnstableApi() {

    }
}