package io.openmarket.dagger.module;

import dagger.Module;
import dagger.Provides;
import io.openmarket.account.dynamodb.UserDao;
import io.openmarket.account.service.AccountServiceHandler;
import io.openmarket.account.service.CredentialManager;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.dao.OrgDao;

import io.openmarket.transaction.dao.dynamodb.TransactionDao;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.service.TransactionServiceHandler;

import javax.inject.Named;
import javax.inject.Singleton;

import static io.openmarket.config.EnvironmentConfig.*;

@Module(includes = {AWSModule.class, DaoModule.class})
public class OpenMarketModule {
    @Provides
    @Singleton
    TransactionServiceHandler provideTransacHandler(final TransactionDao transacDao,
                                                    final SQSTransactionTaskPublisher sqsPublisher,
                                                    @Named(ENV_VAR_TRANSAC_QUEUE_URL) final String queueURL) {
        return new TransactionServiceHandler(transacDao, sqsPublisher, queueURL);
    }

    @Provides
    @Singleton
    AccountServiceHandler provideAccountHandler(final UserDao userDao, final CredentialManager credManager) {
        return new AccountServiceHandler(userDao, credManager);
    }

    @Provides
    @Singleton
    OrgServiceHandler provideOrgHandler(final OrgDao orgDao) {
        return new OrgServiceHandler(orgDao);
    }

    @Provides
    @Named(ENV_VAR_TRANSAC_QUEUE_URL)
    @Singleton
    String provideTransacQueueURL(final EnvMap map) {
        return map.get(ENV_VAR_TRANSAC_QUEUE_URL);
    }

    @Provides
    @Named(ENV_VAR_SERVER_PORT)
    int providePort(final EnvMap env) {
        return Integer.parseInt(env.get(ENV_VAR_SERVER_PORT));
    }

    @Provides
    @Named(ENV_VAR_RPC_USE_VALIDATION)
    boolean provideEnableValidation(final EnvMap env) {
        return Boolean.parseBoolean(env.get(ENV_VAR_RPC_USE_VALIDATION));
    }

    @Provides
    @Named(ENV_VAR_TOKEN_DURATION)
    int provideTokenDuration(final EnvMap env) {
        return Integer.parseInt(env.get(ENV_VAR_TOKEN_DURATION));
    }
}
