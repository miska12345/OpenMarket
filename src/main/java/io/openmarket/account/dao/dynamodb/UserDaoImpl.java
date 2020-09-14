package io.openmarket.account.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.common.collect.ImmutableMap;
import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;

import javax.inject.Inject;
import java.util.*;

import static io.openmarket.config.AccountConfig.*;

public class UserDaoImpl extends AbstractDynamoDBDao<Account> implements UserDao {
    @Inject
    public UserDaoImpl(AmazonDynamoDB dbClient, DynamoDBMapper dbMapper) {
        super(dbClient, dbMapper);
    }

    @Override
    protected boolean validate(Account obj) {
       if (obj.getUsername().length() > ACCOUNT_USERNAME_LENGTH_LIMIT
       ||obj.getUsername().isEmpty()) {
           return false;
       } else if (obj.getPasswordHash().isEmpty()){
           return false;
       } else if (obj.getDisplayName().isEmpty()) {
           return false;
       }
       return true;
    }

    @Override
    public Optional<Account> load(String key) {
        return super.load(Account.class, key);
    }
}
