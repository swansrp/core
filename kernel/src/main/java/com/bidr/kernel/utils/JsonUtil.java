package com.bidr.kernel.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Title: JsonUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 17:53
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
        try {
            result = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {

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
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));
        JavaType javaType = mapper.getTypeFactory().constructParametricType(collectionClass, elementClasses);
        try {
            return mapper.readValue(jsonStr, javaType);
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
}
