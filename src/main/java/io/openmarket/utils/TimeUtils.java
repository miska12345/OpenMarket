package io.openmarket.utils;

import lombok.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static io.openmarket.config.TimeConfig.TIMESTAMP_FORMAT;

public final class TimeUtils {
    public static String formatDate(@NonNull final Date date) {
        final SimpleDateFormat formatter = new SimpleDateFormat(TIMESTAMP_FORMAT);
        return formatter.format(date);
    }

    public static Date parseDate(@NonNull final String timestamp) {
        final SimpleDateFormat formatter = new SimpleDateFormat(TIMESTAMP_FORMAT);
        try {
            return formatter.parse(timestamp);
        } catch (ParseException e) {
            throw new IllegalArgumentException(String.format("The given timestamp has invalid format: %s", timestamp),
                    e);
        }
    }
}
