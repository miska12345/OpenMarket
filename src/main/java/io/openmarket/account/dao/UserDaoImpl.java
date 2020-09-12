package io.openmarket.account.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;
import java.util.Optional;

public class UserDaoImpl extends AbstractDynamoDBDao<Account> implements UserDao {

    public UserDaoImpl(AmazonDynamoDB dbClient, DynamoDBMapper dbMapper) {
        super(dbClient, dbMapper);
    }

    @Override
    protected boolean validate(Account obj) {
        return false;
    }

    @Override
    public Optional<Account> load(String key) {
        return Optional.empty();
    }


    public Optional<String> login(String key) {
        AmazonDynamoDB dbclient = getDbClient();
        return null;

    }
}
