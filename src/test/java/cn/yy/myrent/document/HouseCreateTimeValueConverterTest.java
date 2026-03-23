package cn.yy.myrent.document;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HouseCreateTimeValueConverterTest {

    private final HouseCreateTimeValueConverter converter = new HouseCreateTimeValueConverter();

    @Test
    void shouldReadDateOnlyValueAsStartOfDay() {
        LocalDateTime converted = (LocalDateTime) converter.read("2026-03-18");

        assertEquals(LocalDateTime.of(2026, 3, 18, 0, 0), converted);
    }

    @Test
    void shouldReadIsoDateTimeValue() {
        LocalDateTime converted = (LocalDateTime) converter.read("2026-03-18T22:26:04");

        assertEquals(LocalDateTime.of(2026, 3, 18, 22, 26, 4), converted);
    }

    @Test
    void shouldWriteFullIsoDateTimeValue() {
        String converted = (String) converter.write(LocalDateTime.of(2026, 3, 18, 22, 26, 4));

        assertEquals("2026-03-18T22:26:04", converted);
    }
}
