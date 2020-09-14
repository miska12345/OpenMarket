package io.openmarket.account.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.google.common.collect.ImmutableMap;
import io.jsonwebtoken.security.Keys;
import io.openmarket.account.dao.dynamodb.UserDao;
import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;
import lombok.AccessLevel;
import lombok.Getter;

import javax.crypto.SecretKey;
import javax.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.openmarket.config.AccountConfig.*;

public class UserDaoImpl extends AbstractDynamoDBDao<Account> implements UserDao {

    private final Map<String, String> NAME = ImmutableMap.of("#u", "username");
    private final String KEY_EXPRESSION_USER = "#u = :a";



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
//
//
//
//    public Optional<Account> getUser(String username, String projection) {
//
//        Map<String, AttributeValue> value = ImmutableMap.of(":a", new AttributeValue(username));
//
//        //AmazonDynamoDB dbclient = getDbClient();
//
////      QueryRequest getUserName = new QueryRequest().withTableName("User").withExpressionAttributeNames(NAME)
////                .withExpressionAttributeValues(value)
////                .withKeyConditionExpression("#u = :a").withProjectionExpression("username");
////      QueryResult qr = dbclient.query(getUserName);
////
////        List<Account> result = this.getDbMapper().query(Account.class, new DynamoDBQueryExpression<Account>()
////                .withExpressionAttributeNames(NAME)
////                .withExpressionAttributeValues(value)
////                .withKeyConditionExpression(KEY_EXPRESSION_USER)
////                .withProjectionExpression(projection).withLimit(1))；
//        return null;
//    }


    @Override
    public Optional<Account> load(String key) {
        return super.load(Account.class, key);
    }
}
