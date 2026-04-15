/*
 * Decompiled with CFR 0.152.
 */
package org.nuxeo.ecm.core.search.client.opensearch3.aggregate;

import java.time.ZonedDateTime;

public final class DateHelper {
    public static final String EPOCH_MILLIS_FORMAT = "epoch_millis";

    private DateHelper() {
    }

    public static ZonedDateTime plusDuration(ZonedDateTime origin, String duration) {
        if (duration.matches("[a-zA-Z]+")) {
            return DateHelper.plusDurationAsNoun(origin, duration);
        }
        if (duration.matches("[0-9]+")) {
            return origin.plusNanos(Integer.valueOf(duration) * 1000);
        }
        return DateHelper.plusDurationAsExpression(origin, duration);
    }

    private static ZonedDateTime plusDurationAsExpression(ZonedDateTime origin, String duration) {
        int k = DateHelper.getFactor(duration);
        return switch (duration.substring(duration.length() - 1)) {
            case "s" -> origin.plusSeconds(k);
            case "m" -> origin.plusMinutes(k);
            case "h" -> origin.plusHours(k);
            case "d" -> origin.plusDays(k);
            case "w" -> origin.plusWeeks(k);
            case "M" -> origin.plusMonths(k);
            case "y" -> origin.plusYears(k);
            default -> DateHelper.invalid(duration);
        };
    }

    private static int getFactor(String duration) {
        try {
            return Integer.valueOf(duration.substring(0, duration.length() - 1));
        }
        catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            DateHelper.invalid(duration);
            return 1;
        }
    }

    private static ZonedDateTime plusDurationAsNoun(ZonedDateTime origin, String duration) {
        return switch (duration.toLowerCase()) {
            case "second" -> origin.plusSeconds(1L);
            case "minute" -> origin.plusMinutes(1L);
            case "hour" -> origin.plusHours(1L);
            case "day" -> origin.plusDays(1L);
            case "week" -> origin.plusWeeks(1L);
            case "month" -> origin.plusMonths(1L);
            case "quarter" -> origin.plusMonths(3L);
            case "year" -> origin.plusYears(1L);
            default -> DateHelper.invalid(duration);
        };
    }

    private static ZonedDateTime invalid(String msg) {
        throw new IllegalArgumentException("Invalid duration: " + msg);
    }
}

