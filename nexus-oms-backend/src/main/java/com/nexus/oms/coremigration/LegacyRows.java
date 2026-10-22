package com.nexus.oms.coremigration;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Reading rows of the earlier model ({@code JdbcTemplate.queryForList} maps), the same way in every copy: blank text is
 * no text, a missing number is zero (or null where "not set" matters), and SQL dates become java.time values.
 */
final class LegacyRows {

    /** The identification type that links a copied record to its row in the earlier model. */
    static final String LEGACY_ID = "NEXUS_LEGACY_ID";

    private LegacyRows() {
    }

    /** A text column; null or blank is null. */
    static String text(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value);
    }

    /** A number column; null is zero. */
    static BigDecimal number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    /** A number column; null stays null (for values where "not set" differs from zero). */
    static BigDecimal numberOrNull(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    static LocalDateTime dateTime(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }

    static LocalDate date(Map<String, Object> row, String column) {
        Object value = row.get(column);
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value instanceof LocalDate localDate ? localDate : null;
    }

    /** Trimmed and upper case; null stays null. */
    static String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}
