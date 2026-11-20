package com.example.crm.common;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public final class TimePolicy {

    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Almaty");
    public static final LocalTime WORKDAY_START = LocalTime.of(9, 0);
    public static final LocalTime WORKDAY_END = LocalTime.of(18, 0);
    public static final Duration MEETING_DURATION = Duration.ofMinutes(60);

    private static final DateTimeFormatter STORAGE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter LOCAL_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private TimePolicy() {
    }

    public static String format(Instant instant) {
        return STORAGE_FORMAT.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    public static Instant parseStartAt(String field, String value) {
        OffsetDateTime parsed;
        try {
            parsed = OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(field,
                    "must be an ISO 8601 date-time with timezone, e.g. 2030-01-07T05:00:00Z");
        }
        if (parsed.getSecond() != 0 || parsed.getNano() != 0) {
            throw new ValidationException(field, "must be a whole minute (seconds must be 00)");
        }
        return parsed.toInstant();
    }

    public static Optional<String> workingHoursViolation(Instant start, Instant end) {
        ZonedDateTime localStart = start.atZone(BUSINESS_ZONE);
        ZonedDateTime localEnd = end.atZone(BUSINESS_ZONE);
        DayOfWeek day = localStart.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return Optional.of("Meetings are allowed only Monday to Friday (Asia/Almaty); %s is a %s"
                    .formatted(localStart.toLocalDate(), day));
        }
        boolean fits = localStart.toLocalDate().equals(localEnd.toLocalDate())
                && !localStart.toLocalTime().isBefore(WORKDAY_START)
                && !localEnd.toLocalTime().isAfter(WORKDAY_END);
        if (!fits) {
            return Optional.of("Meeting must fit entirely within 09:00-18:00 Asia/Almaty; requested %s-%s local time"
                    .formatted(LOCAL_TIME_FORMAT.format(localStart), LOCAL_TIME_FORMAT.format(localEnd)));
        }
        return Optional.empty();
    }

    public static DayBounds dayBoundsUtc(LocalDate day) {
        Instant start = day.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        return new DayBounds(start, end);
    }

    public record DayBounds(Instant start, Instant end) {
    }
}
