package io.openmarket.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MiscUtilsTest {
    @Test
    public void testConvertToLastEvaluatedKeyNull() {
        String result = MiscUtils.convertToLastEvaluatedKey(null);
        assertEquals("null", result);
    }
}
