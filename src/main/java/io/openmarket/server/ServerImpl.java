package io.openmarket.server;


import io.grpc.ServerBuilder;
import io.openmarket.server.services.AccountRPCService;
import io.openmarket.server.services.TransactionRPCService;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;

@Log4j2
public class ServerImpl implements Server {
    private final int port;
    private final io.grpc.Server server;

    @Inject
    public ServerImpl(int port, @NonNull final AccountRPCService accountService,
                      @NonNull final TransactionRPCService transactionService) {
        this.port = port;
        this.server = ServerBuilder
                .forPort(port)
                .addService(accountService)
                .addService(transactionService).build();
    }

    @Override
    public void run() {
        try {
            this.server.start();
        } catch(Exception e) {
            log.error("An exception occurred while running the server at port {}", port, e);
        }
    }

    @Override
    public void exit() {
        log.info("Server shutting down");
        this.server.shutdown();
    }
}
