package com.bidr.kernel.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


/**
 * Title: JsonUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 17:53
 */
@Slf4j
public class JsonUtil {

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private JsonUtil() {
    }

    public static String toJson(Object object, boolean needOrder, boolean ignoreEmpty, boolean ignoreIndent,
                                String dateFormat) {
        return generateJsonString(object, needOrder, ignoreEmpty, ignoreIndent, dateFormat);
    }

    private static String generateJsonString(Object object, boolean needOrder, boolean ignoreEmpty,
                                             boolean ignoreIndent, String dateFormat) {
        String result = null;
        ObjectMapper objectMapper = new ObjectMapper();
        // set config of JSON
        // can use single quote
        objectMapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        // allow unquoted field names
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        // ASCII order
        objectMapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, needOrder);
        if (ignoreEmpty) {
            objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        }
        if (!ignoreIndent) {
            objectMapper.configure(SerializationFeature.INDENT_OUTPUT, Boolean.TRUE);
        }
        // set date format
        objectMapper.setDateFormat(new SimpleDateFormat(dateFormat));

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(dateFormat)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(dateFormat)));
        objectMapper.registerModule(javaTimeModule);
        try {
            result = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("JSON转换失败", e);
        }
        return result;
    }

    public static <T> T readJson(Object obj, Class<?> collectionClass, Class<?>... elementClasses) {
        if (obj == null) {
            return null;
        }
        String jsonStr = toJson(obj);
        return readJson(jsonStr, collectionClass, elementClasses);
    }

    /**
     * Transfer object to JSON string
     *
     * @param object
     * @return
     */
    public static String toJson(Object object) {
        return generateJsonString(object, false, false, false);
    }

    /**
     * 获取泛型的Collection Type
     * <p>
     * YourBean bean = (YourBean)readJson(jsonString, YourBean.class)
     * List<YourBean> list = (List<YourBean>)readJson(jsonString, List.class,yourBean.class);
     * Map<H,D> map = (Map<H,D>)readJson(jsonString, HashMap.class,String.class,YourBean.class);
     *
     * @param jsonStr         json字符串
     * @param collectionClass 泛型的Collection
     * @param elementClasses  元素类型
     * @return 返回类型
     */
    public static <T> T readJson(String jsonStr, Class<?> collectionClass, Class<?>... elementClasses) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        registerAutoDateDeserializer(mapper);
        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        try {
            return mapper.readValue(jsonStr, javaType);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }

    }

    public static <T> T readStreamJson(InputStream jsonIs, Class<?> collectionClass, Class<?>... elementClasses) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        registerAutoDateDeserializer(mapper);
        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        try {
            return mapper.readValue(jsonIs, javaType);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }

    }

    private static String generateJsonString(Object object, boolean needOrder, boolean ignoreEmpty,
                                             boolean ignoreIndent) {
        return generateJsonString(object, needOrder, ignoreEmpty, ignoreIndent, DATE_FORMAT);
    }

    public static <T> T readDateJson(Object obj, String dateFormat, Class<?> collectionClass,
                                     Class<?>... elementClasses) {
        if (obj == null) {
            return null;
        }
        String jsonStr = toJson(obj);
        return readDateJson(jsonStr, dateFormat, collectionClass, elementClasses);
    }

    public static <T> T readDateJson(String jsonStr, String dateFormat, Class<?> collectionClass,
                                     Class<?>... elementClasses) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        if (StringUtils.isNotBlank(dateFormat)) {
            mapper.setDateFormat(new SimpleDateFormat(dateFormat));
        } else {
            mapper.setDateFormat(new SimpleDateFormat(DATE_FORMAT));
        }
        registerAutoDateDeserializer(mapper);

        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        try {
            return mapper.readValue(jsonStr, javaType);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }

    }

    public static boolean isJsonValid(String jsonInString) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonInString);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static String toJson(Date object, String dateFormat) {
        return generateJsonString(object, false, false, false, dateFormat);
    }

    public static String toJson(Object object, boolean needOrder, boolean ignoreEmpty, boolean ignoreIndent) {
        return toJsonString(object, needOrder, ignoreEmpty, ignoreIndent);
    }

    public static String toJsonString(Object object, boolean needOrder, boolean ignoreEmpty, boolean ignoreIndent) {
        return generateJsonString(object, needOrder, ignoreEmpty, ignoreIndent, DATE_FORMAT);
    }

    private static void registerAutoDateDeserializer(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, new AutoDateDeserializer());
        mapper.registerModule(module);
    }

    public static class AutoDateDeserializer extends JsonDeserializer<Date> {


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
}

