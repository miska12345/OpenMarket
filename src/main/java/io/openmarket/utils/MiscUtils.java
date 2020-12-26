package io.openmarket.utils;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Optional;

public class MiscUtils {
    private static final Gson GSON = new Gson();
    public static Optional<Map<String, AttributeValue>> getExclusiveStartKey(final String key) {
        try {
            final Type mapType = new TypeToken<Map<String, AttributeValue>>(){}.getType();
            return Optional.ofNullable(GSON.fromJson(key, mapType));
        } catch (JsonSyntaxException e) {
            return Optional.empty();
        }
    }

    public static String convertToLastEvaluatedKey(final Map<String, AttributeValue> key) {
        return GSON.toJson(key);
    }
}
