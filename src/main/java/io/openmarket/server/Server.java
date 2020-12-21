package io.openmarket.server;


import io.grpc.ServerBuilder;
import io.openmarket.server.services.*;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;

import static io.openmarket.config.EnvironmentConfig.ENV_VAR_SERVER_PORT;

@Log4j2
public class Server {
    private final int port;
    private final io.grpc.Server server;

    @Inject
    public Server(@Named(ENV_VAR_SERVER_PORT) int port,
                  @NonNull final AccountRPCService accountService,
                  @NonNull final TransactionRPCService transactionService,
                  @NonNull final OrganizationRPCService orgSerice,
                  @NonNull final StampEventRPCService eventService,
                  @NonNull final OpenMarketInterceptor interceptor,
                  @Nonnull final MarketPlaceRPCService marketplaceService,
                  @Nonnull final NewsFeedRPCService newsfeedService) {
        this.port = port;
        this.server = ServerBuilder
                .forPort(port)
                .addService(accountService)
                .addService(transactionService)
                .addService(orgSerice)
                .addService(eventService)
                .addService(marketplaceService)
                .addService(newsfeedService)
                .intercept(interceptor)
                .build();
    }

    public void run() {
        try {
            this.server.start();
            log.info("Server started at port {}", port);
            this.server.awaitTermination();
        } catch(Exception e) {
            log.error("An exception occurred while running the server at port {}", port, e);
        }
    }

    public void exit() {
        log.info("Server shutting down");
        this.server.shutdown();
    }
}
