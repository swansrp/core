package com.bidr.forge.datasource.service;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.bidr.forge.datasource.dao.entity.SysDataSource;
import com.bidr.forge.datasource.dao.repository.SysDataSourceService;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.jdbc.DynamicDataSourceResolver;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.config.aop.RedisPublish;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: DataSourceCacheService
 * Description: 数据源管理内存缓存（同 SysConfigCacheService 的配置流程）：
 * sys_data_source 全量进内存、按 ds_name 索引，前端保存配置后调用
 * /refresh 触发重建；同时按名称懒加载 Hikari 连接池，刷新即销毁旧池。
 * 作为 DynamicDataSourceResolver 接入 kernel 的 JdbcConnectService：
 * 切换数据源时 yml（spring.datasource.dynamic.datasource）静态定义优先，
 * 未定义的名称由本服务按库表配置动态建池并注册进 dynamic-datasource 路由，
 * dataset/matrix/问数等全链路经 switchDataSource 即可复用。
 * 目前仅支持 MySQL 语法系（ds_type=mysql，jdbc:mysql:// 协议，
 * MySQL/Doris/StarRocks 等走 MySQL 协议的库均可接入）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Service
public class DataSourceCacheService extends DynamicMemoryCache<SysDataSource> implements DynamicDataSourceResolver {

    /** 库表未配置时，yml 兜底数据源的名称 */
    public static final String DEFAULT_NAME = "default";
    public static final String DS_TYPE_MYSQL = "mysql";
    private static final String JDBC_MYSQL_PREFIX = "jdbc:mysql:";

    @Resource
    private SysDataSourceService sysDataSourceService;
    @Lazy
    @Resource
    private DataSourceCacheService self;
    @Resource
    private DataSourceCrypto dataSourceCrypto;
    @Resource
    private DataSource dataSource;

    /** yml 兜底连接（库表未配置任何数据源时生效），原 smartquery.doris.* 改名迁移 */
    @Value("${smartquery.datasource.url:}")
    private String fallbackUrl;
    @Value("${smartquery.datasource.username:}")
    private String fallbackUsername;
    @Value("${smartquery.datasource.password:}")
    private String fallbackPassword;

    /** 动态池参数外部化（各目标库 wait_timeout 等差异较大）：池上限 */
    @Value("${my.datasource.pool.max-pool-size:5}")
    private int poolMaxSize;
    /** 连接最长存活（ms），须小于目标库 wait_timeout，默认 10 分钟 */
    @Value("${my.datasource.pool.max-lifetime:600000}")
    private long poolMaxLifetime;
    /** 空闲保活探测间隔（ms），须小于 max-lifetime，默认 2 分钟 */
    @Value("${my.datasource.pool.keepalive-time:120000}")
    private long poolKeepaliveTime;

    /** 名称 -> 连接池（仅本服务创建的池）；刷新缓存时全部从路由注销并销毁重建 */
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    @Override
    protected Map<String, SysDataSource> getCacheData() {
        List<SysDataSource> list = sysDataSourceService.getDataSourceCache();
        Map<String, SysDataSource> map = ReflectionUtil.reflectToMap(list, SysDataSource::getDsName);
        if (FuncUtil.isEmpty(map) && FuncUtil.isNotEmpty(fallbackUrl)) {
            // 库表未配置时退回 yml 兜底连接，保证老配置平滑可用
            SysDataSource fallback = new SysDataSource();
            fallback.setDsName(DEFAULT_NAME);
            fallback.setDsType(DS_TYPE_MYSQL);
            fallback.setJdbcUrl(fallbackUrl);
            fallback.setUsername(fallbackUsername);
            fallback.setPassword(dataSourceCrypto.decrypt(fallbackPassword));
            fallback.setIsDefault("1");
            map = new HashMap<>();
            map.put(DEFAULT_NAME, fallback);
        }
        return map;
    }

    /** 前端保存/删除配置后触发：注销动态路由、销毁全部连接池并重载内存缓存（多实例经 Redis 广播同步） */
    @RedisPublish
    @Override
    public void refresh() {
        closeAllPools();
        super.refresh();
    }

    /** 按名称取缓存配置，不存在即报错 */
    public SysDataSource getByName(String dsName) {
        SysDataSource ds = self.getCache(dsName);
        if (ds == null) {
            throw new NoticeException("数据源 [" + dsName + "] 未配置，请在数据源管理中维护后刷新缓存");
        }
        return ds;
    }

    /** 取默认数据源：优先 is_default=1，否则取任意一条 */
    public SysDataSource getDefault() {
        Map<String, SysDataSource> map = self.getAllCache();
        if (FuncUtil.isEmpty(map)) {
            return null;
        }
        for (SysDataSource ds : map.values()) {
            if ("1".equals(ds.getIsDefault())) {
                return ds;
            }
        }
        return map.values().iterator().next();
    }

    /**
     * 按名称取数据源：yml 静态定义（master/slave 等）优先直接返回路由中的数据源；
     * 否则按数据源管理配置懒加载 Hikari 连接池
     */
    public DataSource getDataSource(String dsName) {
        DataSource defined = routing() != null ? routing().getDataSources().get(dsName) : null;
        if (defined != null) {
            return defined;
        }
        return pools.computeIfAbsent(dsName, name -> buildPool(getByName(name)));
    }

    /**
     * DynamicDataSourceResolver：切换数据源时 yml 未定义的名称走这里动态提供。
     * 无配置返回 null（kernel 侧按未注册处理，strict=false 时回落 primary）
     */
    @Override
    public DataSource resolve(String name) {
        if (FuncUtil.isEmpty(name) || self.getCache(name) == null) {
            return null;
        }
        return pools.computeIfAbsent(name, n -> buildPool(getByName(n)));
    }

    /**
     * DynamicDataSourceResolver.isAlive：仅对明确已关闭的 Hikari 池判死
     * （refresh 销毁旧池但未及从路由注销时，kernel 侧据此注销重建）；其余一律视为存活
     */
    @Override
    public boolean isAlive(DataSource ds) {
        return !(ds instanceof HikariDataSource && ((HikariDataSource) ds).isClosed());
    }

    /** 可用数据源名称：yml 静态定义 + 数据源管理配置（供前端下拉选择） */
    public List<String> listNames() {
        Set<String> names = new LinkedHashSet<>();
        DynamicRoutingDataSource routing = routing();
        if (routing != null) {
            names.addAll(routing.getDataSources().keySet());
        }
        Map<String, SysDataSource> map = self.getAllCache();
        if (FuncUtil.isNotEmpty(map)) {
            names.addAll(map.keySet());
        }
        return new ArrayList<>(names);
    }

    /** 测试连接（前端“测试”按钮用，不落地、不进池）；行内记录密码为密文，先解密 */
    public void testConnection(SysDataSource ds) {
        validate(ds);
        String password = dataSourceCrypto.decrypt(ds.getPassword());
        try (Connection conn = DriverManager.getConnection(ds.getJdbcUrl(), ds.getUsername(), password)) {
            conn.isValid(10);
        } catch (Exception e) {
            log.error("数据源连接测试失败: {}", e.getMessage());
            throw new NoticeException("连接失败: " + e.getMessage());
        }
    }

    /** MySQL 语法系约束：类型仅 mysql，地址仅 jdbc:mysql: 协议 */
    public static void validate(SysDataSource ds) {
        if (FuncUtil.isEmpty(ds.getDsName())) {
            throw new NoticeException("数据源名称不能为空");
        }
        if (!DS_TYPE_MYSQL.equalsIgnoreCase(ds.getDsType())) {
            throw new NoticeException("目前仅支持 mysql 语法系数据源（MySQL/Doris/StarRocks 等）");
        }
        if (FuncUtil.isEmpty(ds.getJdbcUrl()) || !ds.getJdbcUrl().startsWith(JDBC_MYSQL_PREFIX)) {
            throw new NoticeException("JDBC 连接地址须为 jdbc:mysql:// 形式");
        }
    }

    private DynamicRoutingDataSource routing() {
        return dataSource instanceof DynamicRoutingDataSource ? (DynamicRoutingDataSource) dataSource : null;
    }

    private HikariDataSource buildPool(SysDataSource ds) {
        validate(ds);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(ds.getJdbcUrl());
        config.setUsername(ds.getUsername());
        // 缓存中的密码为落库密文，建池时才解密（历史明文经 decrypt 兜底直通）
        config.setPassword(dataSourceCrypto.decrypt(ds.getPassword()));
        config.setMaximumPoolSize(poolMaxSize);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15000);
        config.setIdleTimeout(300000);
        // 防目标库 wait_timeout 断连：超 maxLifetime 主动换新连接 + 空闲周期保活探测
        config.setMaxLifetime(poolMaxLifetime);
        config.setKeepaliveTime(poolKeepaliveTime);
        config.setPoolName("ds-manage-" + ds.getDsName());
        log.info("数据源管理连接池创建: {}", ds.getDsName());
        return new HikariDataSource(config);
    }

    private void closeAllPools() {
        DynamicRoutingDataSource routing = routing();
        pools.forEach((name, pool) -> {
            try {
                // 先从动态路由注销（kernel 切换时注册进来的），避免刷新后仍路由到旧池
                if (routing != null && routing.getDataSources().get(name) == pool) {
                    routing.removeDataSource(name);
                }
            } catch (Exception e) {
                log.warn("注销动态数据源失败 {}: {}", name, e.getMessage());
            }
            try {
                if (!pool.isClosed()) {
                    pool.close();
                }
            } catch (Exception e) {
                log.warn("关闭数据源连接池失败 {}: {}", name, e.getMessage());
            }
        });
        pools.clear();
    }

    @PreDestroy
    public void destroy() {
        closeAllPools();
    }
}
