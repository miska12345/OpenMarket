package io.openmarket.dagger.component;

import dagger.Component;
import io.openmarket.dagger.module.EnvMap;
import io.openmarket.dagger.module.OpenMarketModule;
import io.openmarket.server.OpenMarketInterceptor;
import io.openmarket.server.Server;
import io.openmarket.server.services.AccountRPCService;
import io.openmarket.server.services.OrderRPCService;
import io.openmarket.server.services.OrganizationRPCService;
import io.openmarket.server.services.TransactionRPCService;

import javax.inject.Singleton;

@Component(modules = {OpenMarketModule.class, EnvMap.class})
@Singleton
public interface OpenMarketComponent {
    TransactionRPCService buildTransacService();
    AccountRPCService buildAccountService();
    OrganizationRPCService buildOrgService();
    Server buildServer();
    OpenMarketInterceptor buildInterceptor();
    OrderRPCService buildOrderService();
}
