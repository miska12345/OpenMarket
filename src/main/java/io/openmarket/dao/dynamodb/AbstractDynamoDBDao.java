package io.openmarket.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.Optional;

@Log4j2
public abstract class AbstractDynamoDBDao<T> implements DynamoDBDao<T> {
    @Getter(AccessLevel.PROTECTED)
    private final AmazonDynamoDB dbClient;

    @Getter(AccessLevel.PROTECTED)
    private final DynamoDBMapper dbMapper;

    public AbstractDynamoDBDao(final AmazonDynamoDB dbClient, final DynamoDBMapper dbMapper) {
        this.dbClient = dbClient;
        this.dbMapper = dbMapper;
    }

    public Optional<T> load(final Class<T> objType, final String key) {
        final T obj = dbMapper.load(objType, key);
        if (obj != null) {
            return Optional.of(obj);
        }
        return Optional.empty();
    }

    public void save(final T obj) {
        if (!validate(obj)) {
            throw new IllegalArgumentException(String.format("The given object failed to validate: %s", obj));
        }
        dbMapper.save(obj);
        log.info("Successfully saved object 'g{}' to DynamoDB", obj);
    }

    protected abstract boolean validate(final T obj);
}
