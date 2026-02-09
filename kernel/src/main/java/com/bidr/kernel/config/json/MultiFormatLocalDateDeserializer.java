package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Title: MultiFormatLocalDateDeserializer
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/2/9 22:14
 */

public class MultiFormatLocalDateDeserializer extends JsonDeserializer<LocalDate> {

    private static final List<DateTimeFormatter> FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    );

    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {

        JsonToken token = p.getCurrentToken();

        if (token == JsonToken.VALUE_NUMBER_INT) {
            return java.time.Instant.ofEpochMilli(p.getLongValue())
                    .atZone(ZoneId.of("Asia/Shanghai"))
                    .toLocalDate();
        }

        if (token == JsonToken.VALUE_STRING) {
            String text = p.getText().trim();
            if (text.isEmpty()) {
                return null;
            }

            for (DateTimeFormatter formatter : FORMATTERS) {
                try {
                    return LocalDate.parse(text, formatter);
                } catch (Exception ignored) {}
            }
        }

        throw new IOException("无法解析 LocalDate: " + p.getText());
    }
}