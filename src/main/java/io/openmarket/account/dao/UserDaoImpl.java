package io.openmarket.account.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.google.common.collect.ImmutableMap;
import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

public class UserDaoImpl extends AbstractDynamoDBDao<Account> implements UserDao {

    private static final Map<String, String> NAME = ImmutableMap.of("#u", "username");
    private static final String KEY_EXPRESSION_USER = "#u = :a";

    @Inject
    public UserDaoImpl(AmazonDynamoDB dbClient, DynamoDBMapper dbMapper) {
        super(dbClient, dbMapper);
    }

    @Override
    protected boolean validate(Account obj) {
        return false;
    }



    public Optional<String> login(String key) {
        AmazonDynamoDB dbclient = getDbClient();
        return null;

    }
//        AmazonDynamoDB dbclient = getDbClient();
//
//        QueryRequest getUserName = new QueryRequest().withTableName("User").withExpressionAttributeNames(NAME)
//                .withExpressionAttributeValues(value)
//                .withKeyConditionExpression("#u = :a").withProjectionExpression("username");
//        QueryResult qr = dbclient.query(getUserName);

    public Optional<Account> getUser(String username, String projection) {

        Map<String, AttributeValue> value = ImmutableMap.of(":a", new AttributeValue(username));


        PaginatedList<Account> result = this.getDbMapper().query(Account.class, new DynamoDBQueryExpression<Account>()
                .withExpressionAttributeNames(NAME)
                .withExpressionAttributeValues(value)
                .withKeyConditionExpression(KEY_EXPRESSION_USER)
                .withProjectionExpression(projection).withLimit(1));

        return Optional.of(result.get(0));

    }


    @Override
    public Optional<Account> load(String key) {
        return super.load(Account.class, key);
    }
}
