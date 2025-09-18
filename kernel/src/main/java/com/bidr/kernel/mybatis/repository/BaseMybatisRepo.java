package com.bidr.kernel.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringPool;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.anno.EnableTruncate;
import com.bidr.kernel.mybatis.dao.mapper.CommonMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.validate.Validator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import com.github.yulichang.toolkit.LambdaUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Title:
 * Description: Copyright: Copyright (c) 2022 Company: bidr
 *
 * @author Sharp
 * @since 2022/10/21 9:55
 */
@Slf4j
@SuppressWarnings("unchecked")
public class BaseMybatisRepo<M extends MyBaseMapper<T>, T> extends MyServiceImpl<M, T> {
    /**
     * 建表语句
     */
    protected static final Map<String, String> DDL_SQL = new ConcurrentHashMap<>();
    /**
     * 版本升级语句
     */
    protected static final Map<String, LinkedHashMap<Integer, String>> UPGRADE_SCRIPTS = new ConcurrentHashMap<>();
    @Resource
    protected PlatformTransactionManager transactionManager;
    Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 1);
    @Resource
    private CommonMapper commonMapper;

    protected static void setCreateDDL(String createSql) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String callerClassName = stack[2].getClassName();
        DDL_SQL.put(callerClassName, createSql);
    }

    protected static void setUpgradeDDL(Integer version, String sql) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String callerClassName = stack[2].getClassName();
        UPGRADE_SCRIPTS.computeIfAbsent(callerClassName, k -> new LinkedHashMap<>()).put(version, sql);
    }

    public String getCreateSql() {
        return DDL_SQL.get(getClass().getName());
    }

    public LinkedHashMap<Integer, String> getUpgradeScripts() {
        return UPGRADE_SCRIPTS.getOrDefault(getClass().getName(), new LinkedHashMap<>());
    }

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

    public MPJLambdaWrapper<T> getMPJLambdaWrapper() {
        return new MPJLambdaWrapper<>();
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
            } else if (validField.isAnnotationPresent(MppMultiId.class)) {
                String colName = validField.getAnnotation(MppMultiId.class).value();
                if (FuncUtil.isNotEmpty(colName)) {
                    col = colName;
                } else {
                    col = field;
                }
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
        TableName annotation = entityClass.getAnnotation(TableName.class);
        if (FuncUtil.isNotEmpty(annotation)) {
            EnableTruncate enableTruncate = this.getClass().getAnnotation(EnableTruncate.class);
            String tableName = annotation.value();
            if (FuncUtil.isNotEmpty(enableTruncate)) {
                log.warn("#### [{}] 清空数据库表 开始 ####", tableName);
                commonMapper.truncate(tableName);
                log.warn("#### [{}] 清空数据库表 结束 ####", tableName);
            } else {
                log.warn("#### [{}] 不支持 清空数据库表 ####", tableName);
            }
        }
    }

    /**
     * 处理sql查询字段名
     *
     * @param columns
     * @return
     */
    public String getSelectSql(SFunction<T, ?>... columns) {
        return Arrays.stream(columns)
                .map(c -> StringUtil.camelToUnderline(LambdaUtils.getName(c)) + " as " + "'" + LambdaUtils.getName(c) +
                        "'")
                .collect(Collectors.joining(StringPool.COMMA, StringPool.SPACE, StringPool.SPACE));
    }

    /**
     * 处理sql查询字段名
     *
     * @param columns
     * @return
     */
    public String getSelectSql(String prefix, SFunction<T, ?>... columns) {
        return Arrays.stream(columns)
                .map(c -> prefix + "." + StringUtil.camelToUnderline(LambdaUtils.getName(c)) + " as " +
                        "'" + LambdaUtils.getName(c) + "'")
                .collect(Collectors.joining(StringPool.COMMA, StringPool.SPACE, StringPool.SPACE));
    }

    public String getSelectSql() {
        StringBuffer sb = new StringBuffer();
        Map<String, Field> map = ReflectionUtil.getFieldMap(entityClass);
        map.forEach((name, field) -> {
            if (!Modifier.isFinal(field.getModifiers())) {
                String columnName = getSelectSqlName(field);
                sb.append(" ").append(columnName).append(" as ").append("'").append(name).append("'").append(" ,");
            }
        });
        String sql = sb.toString();
        return sql.substring(0, sql.length() - 1);
    }

    @Deprecated
    public String getSelectSqlName(Field field) {
        TableField annotation = field.getAnnotation(TableField.class);
        if (FuncUtil.isNotEmpty(annotation)) {
            return annotation.value();
        } else {
            return "`" + StringUtil.camelToUnderline(field.getName()) + "`";
        }
    }

    public String getSelectSql(String prefix) {
        StringBuffer sb = new StringBuffer();
        Map<String, Field> map = ReflectionUtil.getFieldMap(entityClass);
        map.forEach((name, field) -> {
            if (!Modifier.isFinal(field.getModifiers())) {
                String columnName = getSelectSqlName(field);
                sb.append(" ").append(prefix).append(".").append(columnName).append(" as ").append("'").append(name)
                        .append("'").append(" ,");
            }
        });
        String sql = sb.toString();
        return sql.substring(0, sql.length() - 1);
    }

    public List<String> getFieldSql(String prefix) {
        return getFieldSql(prefix, (field) -> true);
    }

    public List<String> getFieldSql(String prefix, Predicate<Field> predicate) {
        List<String> list = new ArrayList<>();
        Map<String, Field> map = ReflectionUtil.getFieldMap(entityClass);
        map.values().stream().filter(predicate).forEach((field) -> {
            if (!Modifier.isFinal(field.getModifiers())) {
                String columnName = getSelectSqlName(field);
                if (FuncUtil.isNotEmpty(prefix)) {
                    list.add(prefix + "." + columnName);
                } else {
                    list.add(columnName);
                }

            }
        });
        return list;
    }

    public List<String> getFieldSql(Predicate<Field> predicate) {
        return getFieldSql("", predicate);
    }

    public List<String> getFieldSql() {
        return getFieldSql("", (field) -> true);
    }

    public List<String> getFieldSql(String prefix, SFunction<T, ?>... columns) {
        List<String> list = new ArrayList<>();
        if (FuncUtil.isNotEmpty(prefix)) {
            return Arrays.stream(columns).map(c -> prefix + "." + getSelectSqlName(LambdaUtil.getField(c)))
                    .collect(Collectors.toList());
        } else {
            return Arrays.stream(columns).map(c -> getSelectSqlName(LambdaUtil.getField(c)))
                    .collect(Collectors.toList());

        }

    }

    public List<T> group(QueryWrapper<T> wrapper, SFunction<T, ?> column, SFunction<T, ?>... columns) {
        List<String> fields = new ArrayList<>();
        fields.add(getSelectSqlName(column));
        if (FuncUtil.isNotEmpty(columns)) {
            for (SFunction<T, ?> c : columns) {
                fields.add(getSelectSqlName(c));
            }
        }
        String[] fieldArray = fields.toArray(new String[0]);
        wrapper.select(fieldArray);
        wrapper.groupBy(fields);
        return super.list(wrapper);
    }

    public String getSelectSqlName(SFunction<T, ?> column) {
        return DbUtil.getSqlColumn(column);
    }

    public String getTableName() {
        TableName annotation = getEntityClass().getAnnotation(TableName.class);
        Validator.assertNotNull(annotation, ErrCodeSys.PA_DATA_NOT_EXIST, "表名");
        return annotation.value();
    }

    public String getIdField() {
        List<String> fieldSql = getFieldSql("t", (field) -> {
            TableId idField = field.getAnnotation(TableId.class);
            return idField != null;
        });
        Validator.assertNotEmpty(fieldSql, ErrCodeSys.PA_PARAM_NULL, "id字段");
        return fieldSql.get(0);
    }

    public void buildSelectWrapper(MPJLambdaWrapper<?> wrapper, Class<?> entityClass, Class<?> resClass) {
        List<Field> fields = ReflectionUtil.getFields(resClass);
        if (FuncUtil.isNotEmpty(fields)) {
            for (Field field : fields) {
                if (!field.isAnnotationPresent(JsonIgnore.class)) {
                    String fieldName = field.getName();
                    if (field.isAnnotationPresent(JsonProperty.class)) {
                        fieldName = field.getAnnotation(JsonProperty.class).value();
                    }
                    Field entityField = ReflectionUtil.getField(entityClass, fieldName);
                    if (FuncUtil.isNotEmpty(entityField)) {
                        String entityFieldSql;
                        if (entityField.isAnnotationPresent(TableField.class)) {
                            entityFieldSql = entityField.getAnnotation(TableField.class).value();
                        } else {
                            entityFieldSql = StringUtil.camelToUnderline(entityField.getName());
                        }
                        wrapper.getSelectColum().add(new SelectString(
                                StringUtil.joinWith(" as ", entityFieldSql, "'" + fieldName + "'"),
                                wrapper.getAlias()));
                    }
                }
            }
        }
    }

    protected TransactionStatus getTransactionStatus() {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transactionManager.getTransaction(def);
    }
}
