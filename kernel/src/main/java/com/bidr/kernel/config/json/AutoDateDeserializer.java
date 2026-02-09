package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Title: AutoDateDeserializer
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/9 20:17
 */

public class AutoDateDeserializer extends JsonDeserializer<Date> {

    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    @Override
    public Date deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {

        JsonToken token = p.getCurrentToken();

        // 1️⃣ 时间戳（UTC 绝对时间）
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return new Date(p.getLongValue());
        }

        // 2️⃣ 字符串
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }

            // 2.1 ISO-8601（带 Z / Offset）
            try {
                OffsetDateTime odt = OffsetDateTime.parse(text);
                return Date.from(odt.atZoneSameInstant(ZONE_CN).toInstant());
            } catch (Exception ignored) {
            }

            try {
                Instant instant = Instant.parse(text);
                return Date.from(instant);
            } catch (Exception ignored) {
            }

            // 2.2 无时区字符串：强制按东八区解析
            for (DateTimeFormatter formatter : FORMATTERS) {

                try {
                    LocalDateTime ldt = LocalDateTime.parse(text, formatter);
                    return Date.from(ldt.atZone(ZONE_CN).toInstant());
                } catch (Exception ignored) {
                }

                try {
                    LocalDate ld = LocalDate.parse(text, formatter);
                    return Date.from(ld.atStartOfDay(ZONE_CN).toInstant());
                } catch (Exception ignored) {
                }
            }
        }

        throw new InvalidFormatException(
                p,
                "无法解析时间字段（已强制按东八区处理）",
                p.getText(),
                Date.class
        );
    }
}