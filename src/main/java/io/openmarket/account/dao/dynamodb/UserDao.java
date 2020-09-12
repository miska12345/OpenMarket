package io.openmarket.account.dao.dynamodb;


import io.openmarket.account.model.Account;
import io.openmarket.dao.dynamodb.DynamoDBDao;

import java.util.Optional;


public interface UserDao extends DynamoDBDao<Account> {
    public Optional<Account> getUser(String username, String projection);

}
