package io.openmarket.dao.dynamodb;

import java.util.Optional;

/**
 * DynamoDBDao can load and save DDB documents.
 * @param <T> the type for dao to use.
 */
public interface DynamoDBDao<T> {
    /**
     * Load an object from DDB using the given key.
     * @param key the hash key of the target object.
     * @return an {@link Optional} object that may contain the loaded object if key is valid, or empty otherwise.
     */
    Optional<T> load(String key);

    /**
     * Upload the given object to DDB.
     * @param obj the obj to upload.
     */
    void save(T obj);
}
