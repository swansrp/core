package com.bidr.kernel.config.response;

import com.bidr.kernel.common.convert.Convert;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                    if (convert.passEntity()) {
                        // passEntity模式：field值 + 整个VO对象
                        if (FuncUtil.isNotEmpty(convert.bean())) {
                            value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(), value, entity);
                        } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                            value = ReflectionUtil.invoke(convert.util(), convert.method(), value, entity);
                        }
                    } else {
                        if (FuncUtil.isNotEmpty(convert.bean())) {
                            value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(), value);
                        } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                            value = ReflectionUtil.invoke(convert.util(), convert.method(), value);
                        }
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
                            if (convert.passEntity()) {
                                // passEntity模式不支持batch，直接处理
                                if (FuncUtil.isNotEmpty(convert.bean())) {
                                    value = ReflectionUtil.invoke(BeanUtil.getBean(convert.bean()), convert.method(),
                                            value, entity);
                                } else if (!FuncUtil.equals(convert.util(), Object.class)) {
                                    value = ReflectionUtil.invoke(convert.util(), convert.method(), value, entity);
                                }
                                ReflectionUtil.setValue(field, entity, value);
                            } else if (convert.batch()) {
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

    // ======================== @BindRepo 字段绑定 ========================

    /**
     * 缓存的 BindRepoHandler，避免每次转换都扫描 Spring 容器
     */
    private static volatile BindRepoHandler cachedHandler;
    private static volatile boolean handlerInitialized = false;

    /**
     * 获取 BindRepoHandler Bean。
     * 采用双重检查锁保证线程安全，仅在首次调用时扫描容器。
     *
     * @return BindRepoHandler，可能为 null
     */
    private static BindRepoHandler getBindRepoHandler() {
        if (!handlerInitialized) {
            synchronized (RespConvert.class) {
                if (!handlerInitialized) {
                    String[] beanNames = BeanUtil.getBeanNamesForType(BindRepoHandler.class);
                    if (FuncUtil.isNotEmpty(beanNames)) {
                        cachedHandler = (BindRepoHandler) BeanUtil.getBean(beanNames[0]);
                    }
                    handlerInitialized = true;
                }
            }
        }
        return cachedHandler;
    }

    /**
     * 单实体 @BindRepo 绑定
     *
     * @param entity  实体
     * @param voClass VO 类型
     * @param <T>     实体类型
     * @param <VO>    VO 类型
     */
    public static <T, VO> void customBindConvert(T entity, Class<VO> voClass) {
        if (FuncUtil.isEmpty(entity)) {
            return;
        }
        List<T> list = new ArrayList<>(1);
        list.add(entity);
        customBindConvert(list, voClass);
    }

    /**
     * 列表 @BindRepo 绑定。
     * <p>
     * 扫描 VO 上所有字段，对于带有 @BindRepo 注解（包括 @BindUser、@BindDept 等通过元注解桥接的）的字段，
     * 批量收集源字段值，一次性查询后回填，避免 N+1。
     *
     * @param entityList 实体列表
     * @param voClass    VO 类型
     * @param <T>        实体类型
     * @param <VO>       VO 类型
     */
    public static <T, VO> void customBindConvert(List<T> entityList, Class<VO> voClass) {
        if (FuncUtil.isEmpty(entityList)) {
            return;
        }
        BindRepoHandler handler = getBindRepoHandler();
        if (handler == null) {
            return;
        }
        List<Field> fields = ReflectionUtil.getFields(voClass);
        if (FuncUtil.isEmpty(fields)) {
            return;
        }

        for (Field field : fields) {
            BindRepo bindRepo = AnnotatedElementUtils.findMergedAnnotation(field, BindRepo.class);
            if (bindRepo == null) {
                continue;
            }
            String sourceFieldName = bindRepo.sourceField();

            // 收集所有实体的源字段值
            Set<Object> sourceValues = new HashSet<>();
            for (T entity : entityList) {
                if (FuncUtil.isNotEmpty(entity)) {
                    Object value = ReflectionUtil.getValue(entity, sourceFieldName, Object.class);
                    if (FuncUtil.isNotEmpty(value)) {
                        sourceValues.add(value);
                    }
                }
            }
            if (sourceValues.isEmpty()) {
                continue;
            }

            // 批量查询
            Map<Object, Object> convertMap = handler.batchConvert(bindRepo, sourceValues);
            if (FuncUtil.isEmpty(convertMap)) {
                continue;
            }

            // 回写到每个实体
            for (T entity : entityList) {
                if (FuncUtil.isNotEmpty(entity)) {
                    Object value = ReflectionUtil.getValue(entity, sourceFieldName, Object.class);
                    if (FuncUtil.isNotEmpty(value)) {
                        Object converted = convertMap.get(value);
                        ReflectionUtil.setValue(field, entity, converted);
                    }
                }
            }
        }
    }

}
