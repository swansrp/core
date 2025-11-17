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
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.validate.Validator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.jeffreyning.mybatisplus.anno.MppMultiId;
import com.github.yulichang.toolkit.LambdaUtils;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
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
@SuppressWarnings("unchecked")
public abstract class BaseMybatisSchema<T> implements MybatisPlusTableInitializerInf {
    protected Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);
    /**
     * 建表语句
     */
    protected static final Map<String, String> DDL_SQL = new ConcurrentHashMap<>();
    /**
     * 版本升级语句
     */
    protected static final Map<String, LinkedHashMap<Integer, String>> UPGRADE_SCRIPTS = new ConcurrentHashMap<>();

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

    public String getTableName() {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        Validator.assertNotNull(annotation, ErrCodeSys.PA_DATA_NOT_EXIST, "表名");
        return annotation.value();
    }
}
