package io.openmarket.example;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperModelFactory;
import io.openmarket.account.dao.UserDao;
import io.openmarket.account.dao.UserDaoImpl;
import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.DynamoDBDao;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HelloWorldTest {
    @Test
    public void testHello() {
        assertEquals("Hello!", new HelloWorld().sayHello());
    }



    @Test
    public void testAccountDao () {
        AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard()
                .withRegion(Regions.US_WEST_2)
                .build();
        DynamoDBMapper mapper = new DynamoDBMapper(client);
        DynamoDBDao<Account> dao = new UserDaoImpl(client, mapper);


    }
}
