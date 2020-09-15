package io.openmarket.server;


import io.grpc.ServerBuilder;
import io.openmarket.server.services.AccountRPCServiceImpl;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.io.IOException;

@Log4j2
public class ServerImpl implements Server {

    private final io.grpc.Server server;

    @Inject
    public ServerImpl(int port, AccountRPCServiceImpl accountRPCHanlder) {
        this.server = ServerBuilder.forPort(port).addService(accountRPCHanlder).build();

    }

    @Override
    public void run() {
        try {
            this.server.start();
        } catch(IOException e) {
            log.error(e.getStackTrace());
        }
    }

    @Override
    public void exit() {
        this.server.shutdown();
    }
}
