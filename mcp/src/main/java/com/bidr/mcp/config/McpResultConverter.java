package com.bidr.mcp.config;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.mcp.anno.McpIgnore;
import io.swagger.annotations.ApiModelProperty;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.noear.solon.ai.chat.tool.ToolCallResultConverter;
import org.noear.solon.core.exception.ConvertException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.*;

/**
 * Title: McpResultConverter
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/9 10:43
 */
@Slf4j
public class McpResultConverter implements ToolCallResultConverter {
    @Override
    public String convert(Object result) throws ConvertException {
        String s = JsonUtil.toJson(converting(result), false, false, true);
        log.info("[MCP]<======={}", s);
        return s;
    }

    private Object converting(Object result) {
        LinkedHashMap<String, Object> res = new LinkedHashMap<>();
        if (result != null) {
            Class<?> clazz = result.getClass();
            if (String.class.isAssignableFrom(clazz)) {
                return result;
            } else if (Collection.class.isAssignableFrom(clazz)) {
                return convertList((Collection<?>) result);
            } else if (result.getClass().isArray()) {
                return convertArray(((Object[]) result));
            } else if (Iterator.class.isAssignableFrom(clazz)) {
                return convertIterator((Iterator<?>) result);
            } else if (Number.class.isAssignableFrom(clazz)) {
                return result;
            } else if (Map.class.isAssignableFrom(clazz)) {
                return result;
            } else if (clazz.getSuperclass() != null) {
                return convertObj(result);
            } else {
                return result;
            }
        }
        return res;
    }

    private List<LinkedHashMap<String, Object>> convertList(Collection<?> list) {
        List<LinkedHashMap<String, Object>> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(list)) {
            for (Object o : list) {
                resList.add(convertObj(o));
            }
        }
        return resList;
    }

    private List<LinkedHashMap<String, Object>> convertArray(Object[] array) {
        List<LinkedHashMap<String, Object>> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(array)) {
            for (Object o : array) {
                resList.add(convertObj(o));
            }
        }
        return resList;
    }

    private LinkedHashMap<String, Object> convertObj(Object obj) {
        LinkedHashMap<String, Object> res = new LinkedHashMap<>();
        if (obj == null) {
            return res;
        }
        Class<?> aClass = obj.getClass();
        List<Field> fieldList = ReflectionUtil.getFields(aClass);
        if (FuncUtil.isEmpty(fieldList)) {
            return res;
        }
        for (Field field : fieldList) {
            if (Modifier.isFinal(field.getModifiers()) || field.isAnnotationPresent(McpIgnore.class)) {
                continue;
            }
            String name = field.getName();
            ApiModelProperty annotation = field.getAnnotation(ApiModelProperty.class);
            if (annotation != null && StringUtils.isNotEmpty((annotation).value())) {
                name = annotation.value();
            }
            Object value = ReflectionUtil.getValue(obj, field);
            if (value != null) {
                if (field.getType().equals(Date.class)) {
                    value = ReflectionUtil.convertDateFormat(field, (Date) value);
                    res.put(name, value);
                } else if (field.getType().equals(BigDecimal.class)) {
                    value = ((BigDecimal) value).toPlainString();
                    res.put(name, value);
                } else {
                    Object subObj = converting(value);
                    if (FuncUtil.isNotEmpty(subObj)) {
                        res.put(name, subObj);
                    } else {
                        res.put(name, value);
                    }
                }
            }
        }
        return res;
    }

    private List<LinkedHashMap<String, Object>> convertIterator(Iterator<?> it) {
        List<LinkedHashMap<String, Object>> resList = new ArrayList<>();
        if (FuncUtil.isNotEmpty(it)) {
            while (it.hasNext()) {
                resList.add(convertObj(it.next()));
            }
        }
        return resList;
    }
}
