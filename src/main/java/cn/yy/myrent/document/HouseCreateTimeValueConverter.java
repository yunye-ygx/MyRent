package cn.yy.myrent.document;

import org.springframework.data.elasticsearch.core.mapping.PropertyValueConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public class HouseCreateTimeValueConverter implements PropertyValueConverter {

    private static final DateTimeFormatter SPACE_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Object write(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        return value.toString();
    }

    @Override
    public Object read(Object value) {
        if (value == null) {
            return null;
        }

        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return OffsetDateTime.parse(text, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDateTime.parse(text, SPACE_DATE_TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
        }

        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Cannot convert Elasticsearch createTime value: " + text, ex);
        }
    }
}
