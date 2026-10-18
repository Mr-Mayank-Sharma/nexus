package com.nexus.oms.jobs;

import com.nexus.oms.exception.BadRequestException;
import org.springframework.scheduling.support.CronExpression;

import java.time.*;

/** Cron handling for jobs: validation, the next fire time, and a floor on how often a job may run. */
final class JobSchedule {

    static final Duration MIN_INTERVAL = Duration.ofMinutes(5);

    private JobSchedule() {}

    /** Validates a six-field cron and timezone; throws a message a person can act on. */
    static void validate(String cron, String timezone) {
        if (cron == null || cron.isBlank()) return;
        ZoneId zone = zone(timezone);
        CronExpression expr;
        try {
            expr = CronExpression.parse(cron.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("The schedule is not valid. It needs six fields: second, minute, hour, day of month, month, day of week (for example 0 0 6 * * * for every day at 06:00). " + e.getMessage());
        }
        ZonedDateTime first = expr.next(ZonedDateTime.now(zone));
        ZonedDateTime second = first == null ? null : expr.next(first);
        if (first == null) throw new BadRequestException("That schedule never fires.");
        if (second != null && Duration.between(first, second).compareTo(MIN_INTERVAL) < 0) {
            throw new BadRequestException("A job may not run more often than every " + MIN_INTERVAL.toMinutes() + " minutes.");
        }
    }

    /** The next fire time after {@code from}, as UTC, or null when there is no schedule. */
    static LocalDateTime next(String cron, String timezone, LocalDateTime fromUtc) {
        if (cron == null || cron.isBlank()) return null;
        ZoneId zone = zone(timezone);
        ZonedDateTime from = fromUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(zone);
        ZonedDateTime next = CronExpression.parse(cron.trim()).next(from);
        return next == null ? null : next.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "UTC" : timezone.trim());
        } catch (DateTimeException e) {
            throw new BadRequestException("Unknown time zone '" + timezone + "'. Use a name such as America/New_York or Asia/Kolkata.");
        }
    }
}
