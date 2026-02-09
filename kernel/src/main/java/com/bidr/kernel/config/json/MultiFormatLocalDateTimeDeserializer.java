package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Title: MultiFormatLocalDateTimeDeserializer
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/9 22:13
 */

public class MultiFormatLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

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
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        JsonToken token = p.getCurrentToken();

        // 1️⃣ 时间戳
        if (token == JsonToken.VALUE_NUMBER_INT) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getLongValue()), ZONE_CN);
        }

        // 2️⃣ 字符串
        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }

            // ISO-8601
            try {
                OffsetDateTime odt = OffsetDateTime.parse(text);
                return odt.atZoneSameInstant(ZONE_CN).toLocalDateTime();
            } catch (Exception ignored) {}

            try {
                Instant instant = Instant.parse(text);
                return LocalDateTime.ofInstant(instant, ZONE_CN);
            } catch (Exception ignored) {}

            // 多格式解析
            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    return LocalDateTime.parse(text, formatter);
                } catch (Exception ignored) {}

                try {
                    return java.time.LocalDate.parse(text, formatter).atStartOfDay();
                } catch (Exception ignored) {}
            }
        }

        throw new IOException("无法解析 LocalDateTime: " + p.getText());
    }
}