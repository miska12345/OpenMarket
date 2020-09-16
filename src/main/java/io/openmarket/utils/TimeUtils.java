package io.openmarket.utils;

import lombok.NonNull;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
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

    public static Date getDateBefore(@NonNull final Date date, int numHours) {
        return getDateAfter(date, -1 * numHours);
    }

    public static Date getDateAfter(@NonNull final Date date, int numHours) {
        final Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.add(Calendar.HOUR_OF_DAY, numHours);
        return calendar.getTime();
    }
}
