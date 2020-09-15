package io.openmarket.dagger.module;

import dagger.Module;
import dagger.Provides;
import io.openmarket.transaction.dao.dynamodb.TransactionDao;
import io.openmarket.transaction.dao.sqs.SQSTransactionTaskPublisher;
import io.openmarket.transaction.service.TransactionServiceHandler;

import javax.inject.Named;
import javax.inject.Singleton;

import static io.openmarket.config.EnvironmentConfig.ENV_VAR_TRANSAC_QUEUE_URL;

@Module(includes = {AWSModule.class, DaoModule.class})
public class OpenMarketModule {
    @Provides
    @Singleton
    TransactionServiceHandler provideTransacHandler(final TransactionDao transacDao,
                                                    final SQSTransactionTaskPublisher sqsPublisher,
                                                    @Named(ENV_VAR_TRANSAC_QUEUE_URL) final String queueURL) {
        return new TransactionServiceHandler(transacDao, sqsPublisher, queueURL);
    }

//    @Provides
//    @Singleton
//    AccountServiceHandler provideAccountHandler(final UserDao userDao, final CredentialManager credManager) {
//        return new AccountServiceHandler(userDao, credManager);
//    }

    @Provides
    @Named(ENV_VAR_TRANSAC_QUEUE_URL)
    @Singleton
    public String provideTransacQueueURL() {
        return "https://sqs.us-west-2.amazonaws.com/185046651126/TransactionTaskQueue";
    }
}
