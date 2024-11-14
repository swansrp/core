package com.bidr.kernel.config.response;

import com.bidr.kernel.common.convert.Convert;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Title: RespConvert
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/11/14 9:19
 */
@SuppressWarnings("unchecked")
public class RespConvert {

    public static <T, VO> void fieldConvert(T entity, Class<VO> voClass) {
        fieldConvert(entity, voClass, false);
    }

    public static <T, VO> void fieldAfterConvert(T entity, Class<VO> voClass) {
        fieldConvert(entity, voClass, true);
    }

    private static <T, VO> void fieldConvert(T entity, Class<VO> voClass, boolean afterBind) {
        ReflectionUtil.getFields(voClass).stream().filter((field) -> field.getAnnotation(Convert.class) != null)
                .forEach(field -> {
                    Convert convert = field.getAnnotation(Convert.class);
                    Object value;
                    if (!FuncUtil.equals(convert.after(), afterBind)) {
                        return;
                    }
                    if (FuncUtil.isNotEmpty(convert.field())) {
                        value = ReflectionUtil.getValue(entity, convert.field(), Object.class);
                    } else {
                        value = ReflectionUtil.getValue(entity, field);
                    }
                    if (FuncUtil.isEmpty(value) && convert.ignoreNull()) {
                        return;
                    }
                    if (FuncUtil.isNotEmpty(convert.bean())) {
                        value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(), value);
                    } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                        value = ReflectionUtil.invoke(convert.util(), convert.method(), value);
                    }
                    ReflectionUtil.setValue(field, entity, value);
                });
    }

    public static <T, VO> void fieldConvert(List<T> entityList, Class<VO> voClass) {
        fieldConvert(entityList, voClass, false);
    }

    public static <T, VO> void fieldAfterConvert(List<T> entityList, Class<VO> voClass) {
        fieldConvert(entityList, voClass, true);
    }


    private static <T, VO> void fieldConvert(List<T> entityList, Class<VO> voClass, boolean afterBind) {
        List<Field> fields = ReflectionUtil.getFields(voClass);
        if (FuncUtil.isNotEmpty(fields)) {
            if (FuncUtil.isNotEmpty(entityList)) {
                Map<Field, Map<Object, Object>> batchValueMap = new HashMap<>(fields.size());
                for (T entity : entityList) {
                    if (FuncUtil.isNotEmpty(entity)) {
                        for (Field field : fields) {
                            Convert convert = field.getAnnotation(Convert.class);
                            if (FuncUtil.isEmpty(convert) || !FuncUtil.equals(convert.after(), afterBind)) {
                                continue;
                            }
                            Object value;
                            if (FuncUtil.isNotEmpty(convert.field())) {
                                value = ReflectionUtil.getValue(entity, convert.field(), Object.class);
                            } else {
                                value = ReflectionUtil.getValue(entity, field);
                            }
                            if (FuncUtil.isEmpty(value) && convert.ignoreNull()) {
                                continue;
                            }
                            if (convert.batch()) {
                                Map<Object, Object> covertValueMap = batchValueMap.getOrDefault(field,
                                        new HashMap<>(entityList.size()));
                                covertValueMap.put(value, null);
                                batchValueMap.put(field, covertValueMap);
                            } else {
                                if (FuncUtil.isNotEmpty(convert.bean())) {
                                    value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(),
                                            value);
                                } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                                    value = ReflectionUtil.invoke(convert.util(), convert.method(), value);
                                }
                                ReflectionUtil.setValue(field, entity, value);
                            }
                        }
                    }
                }
                if (FuncUtil.isNotEmpty(batchValueMap)) {
                    for (Map.Entry<Field, Map<Object, Object>> fieldMapEntry : batchValueMap.entrySet()) {
                        Field field = fieldMapEntry.getKey();
                        Map<Object, Object> covertValueMap = fieldMapEntry.getValue();
                        Convert convert = field.getAnnotation(Convert.class);
                        if (FuncUtil.isNotEmpty(convert.bean())) {
                            covertValueMap = (Map<Object, Object>) ReflectionUtil.invoke(
                                    BeanUtil.getBean(convert.bean()), convert.method(),
                                    new HashSet<>(covertValueMap.keySet()));
                        } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                            covertValueMap = (Map<Object, Object>) ReflectionUtil.invoke(convert.util(),
                                    convert.method(), covertValueMap.keySet());
                        }
                        batchValueMap.put(field, covertValueMap);
                    }
                    for (T entity : entityList) {
                        for (Map.Entry<Field, Map<Object, Object>> fieldMapEntry : batchValueMap.entrySet()) {
                            Field field = fieldMapEntry.getKey();
                            Map<Object, Object> covertValueMap = fieldMapEntry.getValue();
                            ReflectionUtil.setValue(field, entity,
                                    covertValueMap.get(ReflectionUtil.getValue(entity, field)));
                        }
                    }
                }
            }
        }
    }

}
