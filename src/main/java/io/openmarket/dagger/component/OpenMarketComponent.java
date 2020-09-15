package io.openmarket.dagger.component;

import dagger.Component;
import io.openmarket.dagger.module.OpenMarketModule;
import io.openmarket.server.services.TransactionRPCService;

import javax.inject.Singleton;

@Component(modules = OpenMarketModule.class)
@Singleton
public interface OpenMarketComponent {
    TransactionRPCService buildTransacService();
}
