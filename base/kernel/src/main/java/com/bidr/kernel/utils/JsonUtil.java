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

    /**
     * 基础序列化 ObjectMapper（预配置通用设置，使用时 copy() 派生）
     */
    private static final ObjectMapper BASE_SERIALIZER = createBaseSerializer();

    /**
     * 基础反序列化 ObjectMapper（预配置 FAIL_ON_UNKNOWN_PROPERTIES=false + AutoDateDeserializer）
     */
    private static final ObjectMapper BASE_DESERIALIZER = createBaseDeserializer();

    private JsonUtil() {
    }

    // ==================== ObjectMapper 工厂 ====================

    private static ObjectMapper createBaseSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        mapper.setDateFormat(new SimpleDateFormat(DATE_FORMAT));
        // 注册 AutoDateDeserializer 保持与反序列化方法风格一致
        registerAutoDateDeserializer(mapper);
        // 注册 JavaTimeModule 支持 Java 8 时间类型
        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addSerializer(LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        javaTimeModule.addDeserializer(LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        mapper.registerModule(javaTimeModule);
        return mapper;
    }

    private static ObjectMapper createBaseDeserializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        registerAutoDateDeserializer(mapper);
        return mapper;
    }

    // ==================== 序列化 ====================

    public static String toJson(Object object, boolean needOrder, boolean ignoreEmpty, boolean ignoreIndent,
                                String dateFormat) {
        return generateJsonString(object, needOrder, ignoreEmpty, ignoreIndent, dateFormat);
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

    public static String toJson(Date object, String dateFormat) {
        return generateJsonString(object, false, false, false, dateFormat);
    }

    public static String toJson(Object object, boolean needOrder, boolean ignoreEmpty, boolean ignoreIndent) {
        return toJsonString(object, needOrder, ignoreEmpty, ignoreIndent);
    }

    public static String toJsonString(Object object, boolean needOrder, boolean ignoreEmpty, boolean ignoreIndent) {
        return generateJsonString(object, needOrder, ignoreEmpty, ignoreIndent, DATE_FORMAT);
    }

    private static String generateJsonString(Object object, boolean needOrder, boolean ignoreEmpty,
                                             boolean ignoreIndent) {
        return generateJsonString(object, needOrder, ignoreEmpty, ignoreIndent, DATE_FORMAT);
    }

    private static String generateJsonString(Object object, boolean needOrder, boolean ignoreEmpty,
                                             boolean ignoreIndent, String dateFormat) {
        try {
            ObjectMapper mapper = BASE_SERIALIZER.copy();
            mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, needOrder);
            if (ignoreEmpty) {
                mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            }
            if (!ignoreIndent) {
                mapper.configure(SerializationFeature.INDENT_OUTPUT, Boolean.TRUE);
            }
            // 自定义 dateFormat 时需要更新
            if (!DATE_FORMAT.equals(dateFormat)) {
                mapper.setDateFormat(new SimpleDateFormat(dateFormat));
                // 同时更新 LocalDateTime 的序列化/反序列化格式
                JavaTimeModule javaTimeModule = new JavaTimeModule();
                javaTimeModule.addSerializer(LocalDateTime.class,
                        new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(dateFormat)));
                javaTimeModule.addDeserializer(LocalDateTime.class,
                        new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(dateFormat)));
                mapper.registerModule(javaTimeModule);
            }
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("JSON转换失败", e);
            return null;
        }
    }

    // ==================== 反序列化 ====================

    /**
     * 从对象转换为JSON后再反序列化为目标类型
     */
    public static <T> T readJson(Object obj, Class<?> collectionClass, Class<?>... elementClasses) {
        if (obj == null) {
            return null;
        }
        String jsonStr = toJson(obj);
        return readJson(jsonStr, collectionClass, elementClasses);
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
     * @return 返回类型，解析失败返回null
     */
    public static <T> T readJson(String jsonStr, Class<?> collectionClass, Class<?>... elementClasses) {
        try {
            return readJsonOrThrow(jsonStr, collectionClass, elementClasses);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    /**
     * 获取泛型的Collection Type（解析失败时抛出异常）
     * <p>
     * 与 readJson 功能相同，但在解析失败时抛出 JsonProcessingException，
     * 调用方可以区分"输入为空"和"解析失败"。
     *
     * @param jsonStr         json字符串
     * @param collectionClass 泛型的Collection
     * @param elementClasses  元素类型
     * @return 返回类型
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static <T> T readJsonOrThrow(String jsonStr, Class<?> collectionClass, Class<?>... elementClasses)
            throws JsonProcessingException {
        ObjectMapper mapper = BASE_DESERIALIZER.copy();
        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        return mapper.readValue(jsonStr, javaType);
    }

    public static <T> T readStreamJson(InputStream jsonIs, Class<?> collectionClass, Class<?>... elementClasses) {
        try {
            return readStreamJsonOrThrow(jsonIs, collectionClass, elementClasses);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    /**
     * 从 InputStream 读取 JSON 并反序列化（解析失败时抛出异常）
     *
     * @param jsonIs          输入流
     * @param collectionClass 泛型的Collection
     * @param elementClasses  元素类型
     * @return 返回类型
     * @throws IOException 解析失败时抛出
     */
    public static <T> T readStreamJsonOrThrow(InputStream jsonIs, Class<?> collectionClass, Class<?>... elementClasses)
            throws IOException {
        ObjectMapper mapper = BASE_DESERIALIZER.copy();
        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        return mapper.readValue(jsonIs, javaType);
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
        try {
            return readDateJsonOrThrow(jsonStr, dateFormat, collectionClass, elementClasses);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    /**
     * 指定日期格式的 JSON 反序列化（解析失败时抛出异常）
     *
     * @param jsonStr         json字符串
     * @param dateFormat      日期格式
     * @param collectionClass 泛型的Collection
     * @param elementClasses  元素类型
     * @return 返回类型
     * @throws JsonProcessingException 解析失败时抛出
     */
    public static <T> T readDateJsonOrThrow(String jsonStr, String dateFormat, Class<?> collectionClass,
                                            Class<?>... elementClasses) throws JsonProcessingException {
        ObjectMapper mapper = BASE_DESERIALIZER.copy();
        mapper.setDateFormat(new SimpleDateFormat(StringUtils.isNotBlank(dateFormat) ? dateFormat : DATE_FORMAT));
        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        return mapper.readValue(jsonStr, javaType);
    }

    // ==================== 工具方法 ====================

    public static boolean isJsonValid(String jsonInString) {
        try {
            final ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(jsonInString);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void registerAutoDateDeserializer(ObjectMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Date.class, new AutoDateDeserializer());
        mapper.registerModule(module);
    }

    // ==================== 自定义反序列化器 ====================

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

                // 2.2 纯数字字符串 → 尝试作为毫秒时间戳解析（阈值为2001-09-09，排除年份等短数字）
                try {
                    long timestamp = Long.parseLong(text);
                    if (timestamp > 1_000_000_000_000L) {
                        return new Date(timestamp);
                    }
                } catch (NumberFormatException ignored) {
                }

                // 2.3 无时区字符串：强制按东八区解析
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
