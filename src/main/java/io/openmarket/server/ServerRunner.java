package io.openmarket.server;

import io.openmarket.dagger.component.DaggerOpenMarketComponent;
import io.openmarket.dagger.component.OpenMarketComponent;
import io.openmarket.dagger.module.EnvMap;

public class ServerRunner {
    public static void main(String[] args) {
        final EnvMap map = new EnvMap().withMap(System.getenv());
        final OpenMarketComponent comp = DaggerOpenMarketComponent.builder().envMap(map).build();
        final Server server = comp.buildServer();
        server.run();
    }
}
