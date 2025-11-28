package com.bidr.kernel.mybatis.repository;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.mybatis.inf.MybatisPlusTableInitializerInf;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title:
 * Description: Copyright: Copyright (c) 2022 Company: bidr
 *
 * @author Sharp
 * @since 2022/10/21 9:55
 */
@SuppressWarnings("unchecked")
public abstract class BaseMybatisSchema<T> implements MybatisPlusTableInitializerInf {
    /**
     * 建表语句
     */
    protected static final Map<String, String> DDL_SQL = new ConcurrentHashMap<>();
    /**
     * DDL版本升级语句
     */
    protected static final Map<String, LinkedHashMap<Integer, String>> UPGRADE_SCRIPTS = new ConcurrentHashMap<>();
    /**
     * DML数据初始化脚本（仅在新建表时执行一次）
     */
    protected static final Map<String, List<String>> INIT_DATA_SCRIPTS = new ConcurrentHashMap<>();
    protected Class<T> entityClass = (Class<T>) ReflectionUtil.getSuperClassGenericType(this.getClass(), 0);

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

    /**
     * 设置初始化数据脚本（DML）
     * <p>
     * 注意：
     * <ul>
     *     <li>仅在新建表时执行，已存在的表不会重复执行</li>
     *     <li>按添加顺序执行，无需版本号</li>
     *     <li>建议使用 INSERT IGNORE 或 INSERT ... ON DUPLICATE KEY UPDATE 避免重复数据</li>
     * </ul>
     * </p>
     *
     * @param sql DML SQL语句
     */
    protected static void setInitData(String sql) {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        String callerClassName = stack[2].getClassName();
        INIT_DATA_SCRIPTS.computeIfAbsent(callerClassName, k -> new ArrayList<>()).add(sql);
    }

    @Override
    public String getCreateSql() {
        return DDL_SQL.get(getClass().getName());
    }

    @Override
    public LinkedHashMap<Integer, String> getUpgradeScripts() {
        return UPGRADE_SCRIPTS.getOrDefault(getClass().getName(), new LinkedHashMap<>());
    }

    @Override
    public List<String> getInitDataScripts() {
        return INIT_DATA_SCRIPTS.getOrDefault(getClass().getName(), new ArrayList<>());
    }

    @Override
    public String getTableName() {
        TableName annotation = entityClass.getAnnotation(TableName.class);
        Validator.assertNotNull(annotation, ErrCodeSys.PA_DATA_NOT_EXIST, "表名");
        return annotation.value();
    }
}
