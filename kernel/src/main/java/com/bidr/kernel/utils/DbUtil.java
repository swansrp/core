/**
 * Title: DbUtil
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 * @description Project Name: Tanya
 * Package: com.srct.service.utils
 * @since 2019-4-4 15:48
 */
package com.bidr.kernel.utils;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class DbUtil {

    public static <T extends SFunction> String getSqlColumn(T column) {
        Field field = LambdaUtil.getField(column);
        TableField annotation = field.getAnnotation(TableField.class);
        return FuncUtil.isNotEmpty(annotation) ? annotation.value() :
                "`" + StringUtil.camelToUnderline(field.getName()) + "`";
    }

    @SuppressWarnings("rawtypes")
    public static <K, T> Page<T> copy(Page<K> source, Class<T> clazz) {
        Page<T> res = new Page(source.getCurrent(), source.getSize());
        if (CollectionUtils.isNotEmpty(source.getRecords())) {
            List<T> tempList = ReflectionUtil.copyList(source.getRecords(), clazz);
            res.setRecords(tempList);
        }
        res.setTotal(source.getTotal());
        return res;
    }

    public static <T> List<T> resultMapConvert(List<?> dataList, Class<T> targetClass) {
        return resultMapConvert(dataList, targetClass, false);
    }

    /**
     * 将多表联查结果映射成 一对多/一对一 对象形如
     * class extends oneClass {
     *
     * @param dataList        多表联查原始数据
     * @param targetClass     用转换的对象类型
     * @param <T>             对象类型
     * @param filterDuplicate 是否过滤重复对象
     * @return 拼接后的对象
     * @MyResultMap private toOneClass one
     * private List<toManyClass> many;
     * }
     * 一对一 查找 标注@MyResultMap注解的对象
     * 一对多 查找List类型属性进行映射
     * 其他属性直接按照名称和类型赋值
     * 即只支持List类型进行一对多属性映射
     */

    public static <T> List<T> resultMapConvert(List<?> dataList, Class<T> targetClass, Boolean filterDuplicate) {
        List<Field> resultMapFieldList = ReflectionUtil.getFields(targetClass).stream()
                .filter(field -> field.getType().equals(List.class) || field.getAnnotation(MyResultMap.class) != null)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(resultMapFieldList)) {
            if (filterDuplicate) {
                return new ArrayList(ReflectionUtil.copySet(dataList, targetClass));
            } else {
                return ReflectionUtil.copyList(dataList, targetClass);
            }
        }
        List<T> res = new ArrayList<>();

        Map<T, List<Object>> map = new LinkedHashMap<>();
        if (dataList.get(0) instanceof Map) {
            for (Object data : dataList) {
                T parentObj = (T) ReflectionUtil.copy((Map) data, targetClass);
                List<Object> many = map.getOrDefault(parentObj, new ArrayList<>());
                many.add(data);
                map.put(parentObj, many);
            }
        } else {
            for (Object data : dataList) {
                T parentObj = ReflectionUtil.copy(data, targetClass);
                List<Object> many = map.getOrDefault(parentObj, new ArrayList<>());
                many.add(data);
                map.put(parentObj, many);
            }
        }

        for (Map.Entry<T, List<Object>> entry : map.entrySet()) {
            for (Field resultMapField : resultMapFieldList) {
                Class<?> resultMapClass = resultMapField.getType();
                if (resultMapField.getAnnotation(MyResultMap.class) != null) {
                    filterDuplicate =
                            filterDuplicate || resultMapField.getAnnotation(MyResultMap.class).filterDuplicate();
                }
                boolean isCollectResultMap = (List.class).equals(resultMapClass);
                if (isCollectResultMap) {
                    ParameterizedType genericType = (ParameterizedType) resultMapField.getGenericType();
                    resultMapClass = (Class<?>) genericType.getActualTypeArguments()[0];
                }

                List<?> list = resultMapConvert(entry.getValue(), resultMapClass, filterDuplicate);
                if (isCollectResultMap) {
                    ReflectionUtil.setValue(resultMapField, entry.getKey(), list);
                } else {
                    ReflectionUtil.setValue(resultMapField, entry.getKey(), list.get(0));
                }
            }
            res.add(entry.getKey());
        }
        return res;
    }

    public static void setCreateAtTimeStamp(Object entity) {
        if (ReflectionUtil.existedField(entity.getClass(), SqlConstant.CREATE_FIELD)) {
            ReflectionUtil.setFieldValue(entity, SqlConstant.CREATE_FIELD, new Date());
        }
    }

    public static void setUpdateAtTimeStamp(Object entity) {
        if (ReflectionUtil.existedField(entity.getClass(), SqlConstant.UPDATE_FIELD)) {
            ReflectionUtil.setFieldValue(entity, SqlConstant.UPDATE_FIELD, new Date());
        }
    }

    public static void setCreateAtTimeStamp(Object entity, Date date) {
        if (ReflectionUtil.existedField(entity.getClass(), SqlConstant.CREATE_FIELD)) {
            ReflectionUtil.setFieldValue(entity, SqlConstant.CREATE_FIELD, date);
        }
    }

    public static void setUpdateAtTimeStamp(Object entity, Date date) {
        if (ReflectionUtil.existedField(entity.getClass(), SqlConstant.UPDATE_FIELD)) {
            ReflectionUtil.setFieldValue(entity, SqlConstant.UPDATE_FIELD, date);
        }
    }

    public static <T> T buildEntity(Map<String, Object> entityMap, Class<T> clazz) {
        return ReflectionUtil.copy(entityMap, clazz);
    }

    public static <T, R> Page<R> page(Page<T> page, List<R> targetList) {
        Page<R> res = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        res.setRecords(targetList);
        return res;
    }

    public static <T, R> Page<R> page(Page<T> page, Class<R> clazz) {
        Page<R> res = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<R> targetList = ReflectionUtil.copyList(page.getRecords(), clazz);
        res.setRecords(targetList);
        return res;
    }

    public static String getTableName(Class<?> clazz) {
        TableName annotation = clazz.getAnnotation(TableName.class);
        Validator.assertNotNull(annotation, ErrCodeSys.PA_DATA_NOT_EXIST, "表名");
        String[] split = annotation.value().split("\\.");
        return split[split.length - 1];
    }

    public static String getSelectSqlName(Class<?> clazz, String fieldName) {
        Field field = ReflectionUtil.getField(clazz, fieldName);
        TableField annotation = field.getAnnotation(TableField.class);
        if (FuncUtil.isNotEmpty(annotation)) {
            return annotation.value();
        } else {
            return "`" + StringUtil.camelToUnderline(field.getName()) + "`";
        }
    }

    public static String getSelectSqlName(GetFunc fieldFunc) {
        Field field = LambdaUtil.getFieldByGetFunc(fieldFunc);
        TableField annotation = field.getAnnotation(TableField.class);
        if (FuncUtil.isNotEmpty(annotation)) {
            return annotation.value();
        } else {
            return "`" + StringUtil.camelToUnderline(field.getName()) + "`";
        }
    }

    public static String apply(String sql, String applyJsonStr) {
        Map<String, Object> applyMap = JsonUtil.readJson(applyJsonStr, Map.class, String.class, Object.class);
        if (FuncUtil.isNotEmpty(applyMap)) {
            for (Map.Entry<String, Object> entry : applyMap.entrySet()) {
                if (FuncUtil.isNotEmpty(entry.getValue())) {
                    if (entry.getValue() instanceof Collection) {
                        List<String> array = new ArrayList<>();
                        if (FuncUtil.isNotEmpty(entry.getValue())) {
                            for (Object o : (Collection<?>) entry.getValue()) {
                                array.add(StringUtil.parse(o));
                            }
                            sql = sql.replace("${" + entry.getKey() + "}", "'" + StringUtil.joinWith(",", array) + "'");
                        }
                    } else {
                        sql = sql.replace("${" + entry.getKey() + "}", entry.getValue().toString());
                    }
                }
            }
        }
        return sql;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD})
    public @interface MyResultMap {
        String value() default "";

        boolean filterDuplicate() default false;
    }
}
