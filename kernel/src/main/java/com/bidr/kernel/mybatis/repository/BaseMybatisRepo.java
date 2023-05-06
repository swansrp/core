package com.bidr.kernel.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import com.github.yulichang.toolkit.LambdaUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Title:
 * Description: Copyright: Copyright (c) 2022 Company: bidr
 *
 * @author Sharp
 * @date 2022/10/21 9:55
 */
@SuppressWarnings("unchecked")
public class BaseMybatisRepo<M extends MyBaseMapper<T>, T> extends MyServiceImpl<M, T> {
    Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);

    public QueryWrapper<T> getQueryWrapper(String fieldName, Object value) {
        QueryWrapper<T> wrapper = Wrappers.query();
        wrapper.eq(fieldName, value);
        return wrapper;
    }

    public UpdateWrapper<T> getUpdateWrapper(String fieldName, Object value) {
        UpdateWrapper<T> wrapper = Wrappers.update();
        wrapper.eq(fieldName, value);
        return wrapper;
    }

    public QueryWrapper<T> getQueryWrapper(T entity) {
        return Wrappers.query(entity);
    }

    public LambdaQueryWrapper<T> getQueryWrapper() {
        return Wrappers.lambdaQuery(entityClass);
    }

    public LambdaUpdateWrapper<T> getUpdateWrapper() {
        return Wrappers.lambdaUpdate(entityClass);
    }

    public UpdateWrapper<T> getIdWrapper(T entity) {
        List<Field> fieldArray = ReflectionUtil.getFields(entity);
        List<String> multiIdFieldNameList = new ArrayList<>();
        String idFieldName = null;
        for (Field field : fieldArray) {
            MppMultiId mppMultiId = field.getAnnotation(MppMultiId.class);
            if (mppMultiId != null) {
                multiIdFieldNameList.add(field.getName());
            }
            TableId tableId = field.getAnnotation(TableId.class);
            if (tableId != null) {
                idFieldName = field.getName();
            }
        }
        if (StringUtils.isNotBlank(idFieldName)) {
            return getUpdateWrapper(entity, idFieldName);
        } else if (CollectionUtils.isNotEmpty(multiIdFieldNameList)) {
            return getUpdateWrapper(entity, multiIdFieldNameList.toArray(new String[0]));
        } else {
            return getUpdateWrapper(entity);
        }
    }

    public UpdateWrapper<T> getUpdateWrapper(T entity, String... fieldNameArray) {
        Map<String, Object> properties = buildPropertiesMap(entity, fieldNameArray);
        return getUpdateWrapperByMap(properties);
    }

    public UpdateWrapper<T> getUpdateWrapper(T entity) {
        return Wrappers.update(entity);
    }

    @NotNull
    private <T> Map<String, Object> buildPropertiesMap(T entity, String[] fieldNameArray) {
        Map<String, Object> properties = new HashMap<>(fieldNameArray.length);
        for (String field : fieldNameArray) {
            Field validField = ReflectionUtil.getField(entity.getClass(), field);
            Validator.assertNotNull(validField, ErrCodeSys.SYS_ERR_MSG, "对象属性:" + field + "不存在");
            Object value = ReflectionUtil.getValue(entity, validField);
            String col;
            if (validField.isAnnotationPresent(TableField.class)) {
                col = validField.getAnnotation(TableField.class).value();
            } else if (validField.isAnnotationPresent(TableId.class)) {
                col = validField.getAnnotation(TableId.class).value();
            } else {
                col = field;
            }
            properties.put(col, value);
        }
        return properties;
    }

    public UpdateWrapper<T> getUpdateWrapperByMap(Map<String, Object> propertyMap) {
        UpdateWrapper<T> wrapper = Wrappers.update();
        wrapper.allEq(propertyMap);
        return wrapper;
    }

    public QueryWrapper<T> getQueryWrapper(T entity, String... fieldNameArray) {
        Map<String, Object> properties = buildPropertiesMap(entity, fieldNameArray);
        return getQueryWrapperByMap(properties);
    }

    public QueryWrapper<T> getQueryWrapperByMap(Map<String, Object> propertyMap) {
        QueryWrapper<T> wrapper = Wrappers.query();
        wrapper.allEq(propertyMap);
        return wrapper;
    }

    public QueryWrapper<T> getQueryWrapper(String propertyName, List<?> propertyList) {
        QueryWrapper<T> wrapper = Wrappers.query();
        wrapper.in(propertyName, propertyList);
        return wrapper;
    }

    public UpdateWrapper<T> getUpdateWrapper(String propertyName, List<?> propertyList) {
        UpdateWrapper<T> wrapper = Wrappers.update();
        wrapper.in(propertyName, propertyList);
        return wrapper;
    }

    public void fillUpdateWrapper(T entity, UpdateWrapper<T> wrapper) {
        Map<String, Object> map = super.getHashMap(entity);
        fillUpdateWrapper(map, wrapper);
    }

    public void fillUpdateWrapper(Map<String, Object> propertyMap, UpdateWrapper<T> wrapper) {
        propertyMap.forEach(wrapper::set);
    }

    public boolean multiIdExisted() {
        Field[] fieldArray = entityClass.getDeclaredFields();
        for (Field field : fieldArray) {
            MppMultiId mppMultiId = field.getAnnotation(MppMultiId.class);
            if (mppMultiId != null) {
                return true;
            }
        }
        return false;
    }

    public Serializable getId(T entity) {
        Field[] fieldArray = entityClass.getDeclaredFields();
        for (Field field : fieldArray) {
            TableId idField = field.getAnnotation(TableId.class);
            if (idField != null) {
                return ReflectionUtil.getValue(entity, field);
            }
        }
        return null;
    }

    public void truncate() {

    }

    /**
     * 处理sql查询字段名
     *
     * @param columns
     * @return
     */
    protected String getSelectSql(SFunction<T, ?>... columns) {
        return Arrays.stream(columns)
                .map(c -> StringUtil.camelToUnderline(LambdaUtils.getName(c)) + " as " + LambdaUtils.getName(c))
                .collect(Collectors.joining(StringPool.COMMA, StringPool.SPACE, StringPool.SPACE));
    }

    protected String getSelectSql() {
        StringBuffer sb = new StringBuffer();
        Map<String, Field> map = ReflectionUtil.getFieldMap(entityClass);
        map.forEach((name, field) -> {
            if (!Modifier.isFinal(field.getModifiers())) {
                sb.append(" `").append(StringUtil.camelToUnderline(name)).append("` as ").append(name).append(" ,");
            }
        });
        String sql = sb.toString();
        return sql.substring(0, sql.length() - 1);
    }

    protected String getSelectSqlName(SFunction<T, ?> column) {
        return LambdaUtils.getName(column);
    }


}
