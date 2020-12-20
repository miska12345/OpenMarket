package io.openmarket.dagger.module;

import dagger.Module;
import dagger.Provides;
import io.openmarket.account.dynamodb.UserDao;
import io.openmarket.account.service.AccountServiceHandler;
import io.openmarket.account.service.CredentialManager;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.dao.OrgDao;
import io.openmarket.stamp.dao.dynamodb.StampEventDao;
import io.openmarket.stamp.service.StampEventServiceHandler;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.service.TransactionServiceHandler;
import io.openmarket.wallet.dao.dynamodb.WalletDao;
import org.omg.SendingContext.RunTimeOperations;

import javax.inject.Named;
import javax.inject.Singleton;

import java.sql.Connection;
import java.sql.DriverManager;

import static io.openmarket.config.EnvironmentConfig.*;

@Module(includes = {AWSModule.class, DaoModule.class})
public class OpenMarketModule {
    @Provides
    @Singleton
    TransactionServiceHandler provideTransacHandler(final TransactionDao transacDao,
                                                    final WalletDao walletDao,
                                                    final SQSTransactionTaskPublisher sqsPublisher,
                                                    @Named(ENV_VAR_TRANSAC_QUEUE_URL) final String queueURL) {
        return new TransactionServiceHandler(transacDao, walletDao, sqsPublisher, queueURL);
    }

    @Provides
    @Singleton
    AccountServiceHandler provideAccountHandler(final UserDao userDao,
                                                final CredentialManager credManager,
                                                final TransactionServiceHandler transactionServiceHandler) {
        return new AccountServiceHandler(userDao, credManager, transactionServiceHandler);
    }

    @Provides
    @Singleton
    OrgServiceHandler provideOrgHandler(final OrgDao orgDao) {
        return new OrgServiceHandler(orgDao);
    }

    @Provides
    @Singleton
    StampEventServiceHandler provideStampEventHandler(final StampEventDao eventDao,
                                                      final TransactionServiceHandler transactionServiceHandler) {
        return new StampEventServiceHandler(eventDao, transactionServiceHandler);
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
