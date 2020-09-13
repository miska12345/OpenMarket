package io.openmarket.account.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.document.KeyConditions;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableMap;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

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

    public static Optional<String> hash(byte[] password, byte[] salt) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
            byte[] hashedPassword = md.digest(password);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashedPassword)
                sb.append(String.format("%02x", b));
            return Optional.of(sb.toString());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return Optional.empty();

    }

    public int getCount (){
//        QueryRequest
        return 0;
    }

    public Optional<String> login(String username, byte[] password) {
        AmazonDynamoDB dbclient = getDbClient();
        Map<String, String> name = ImmutableMap.of(":#u", "username");
        Map<String, AttributeValue> value = ImmutableMap.of(":a", new AttributeValue(username));

        QueryResult result = dbclient.query(new QueryRequest().withTableName("User").withExpressionAttributeNames(name)
                .withExpressionAttributeValues(value).withKeyConditionExpression("#u = :a").withLimit(1));
        if (result.getCount() == 0) return Optional.empty();
        ByteBuffer passwordSalt = result.getItems().get(0).get("passwordSalt").getB();
        ByteBuffer passwordSalted = result.getItems().get(0).get("password").getB();
        if (!hash(password, passwordSalt.array()).get().getBytes().equals(passwordSalted)) return Optional.empty();
        SecretKey key = Keys.hmacShaKeyFor(username.getBytes(StandardCharsets.UTF_8));
        JwtBuilder jws = Jwts.builder().setSubject(username).signWith(key);
        return Optional.of(jws.compact());

    }
}
