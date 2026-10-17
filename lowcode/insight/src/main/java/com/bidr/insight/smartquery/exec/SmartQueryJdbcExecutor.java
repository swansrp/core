package com.bidr.insight.smartquery.exec;

import com.bidr.forge.datasource.dao.entity.SysDataSource;
import com.bidr.forge.datasource.service.DataSourceCacheService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.insight.smartquery.dao.entity.InsightAgent;
import com.bidr.insight.smartquery.dao.repository.InsightAgentService;
import com.bidr.insight.smartquery.semantic.SemanticLayerRegistry;
import com.bidr.insight.smartquery.model.QueryRows;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: SmartQueryJdbcExecutor
 * Description: 问数只读执行器（MySQL 语法系，JDBC 直连）。
 * 安全约束：只执行 SqlGenerator 产出的参数化 SQL；白名单仅 SELECT/WITH；
 * PreparedStatement 参数绑定；查询超时保护。
 * 连接取自 DataSourceCacheService：默认 Agent 用默认数据源，其余 Agent 按
 * insight_agent 绑定（ds_name）解析（内存缓存，增删改/刷新时失效），多个 Agent
 * 可共用同一数据源；绑定缺失时兼容旧约定回落同名数据源
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Slf4j
@Service
public class SmartQueryJdbcExecutor {

    @Value("${smartquery.datasource.query-timeout-seconds:30}")
    private int queryTimeoutSeconds;

    @Resource
    private DataSourceCacheService dataSourceCacheService;

    @Resource
    private SemanticLayerRegistry layers;

    @Resource
    private InsightAgentService insightAgentService;

    /** agentCode → 绑定 dsName 缓存：执行链路高频，避免每次查 insight_agent；
     *  仅缓存命中绑定，Agent 增删改/缓存刷新时经 {@link #evictAgentDs} 失效 */
    private final Map<String, String> agentDsCache = new ConcurrentHashMap<>();

    /** 失效绑定缓存：传 null 清空全部（刷新端点），否则移除指定 Agent */
    public void evictAgentDs(String agentCode) {
        if (agentCode == null) {
            agentDsCache.clear();
        } else {
            agentDsCache.remove(agentCode);
        }
    }

    /** 未配置数据源时返回 false，由上层走降级提示 */
    public boolean isConfigured() {
        return dataSourceCacheService != null && dataSourceCacheService.getDefault() != null;
    }

    public QueryRows execute(String sql, List<Object> params) {
        if (dataSourceCacheService == null) {
            throw new IllegalStateException("未配置问数数据源（数据源管理页面维护，或 smartquery.datasource.url 兜底）");
        }
        String agentCode = layers == null ? SemanticLayerRegistry.DEFAULT_AGENT : layers.currentAgentCode();
        SysDataSource ds = resolveDataSource(agentCode);
        if (ds == null) {
            throw new IllegalStateException("未配置问数数据源（数据源管理页面维护，或 smartquery.datasource.url 兜底）");
        }
        String head = sql.trim();
        String upper = head.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new IllegalArgumentException("仅允许执行 SELECT/WITH 查询");
        }
        QueryRows out = new QueryRows();
        DataSource pool = dataSourceCacheService.getDataSource(ds.getDsName());
        try (Connection conn = pool.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setQueryTimeout(queryTimeoutSeconds);
            List<Object> ps2 = params == null ? new ArrayList<Object>() : params;
            for (int i = 0; i < ps2.size(); i++) {
                ps.setObject(i + 1, ps2.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                for (int i = 1; i <= n; i++) {
                    out.getColumns().add(md.getColumnLabel(i));
                }
                while (rs.next()) {
                    List<Object> row = new ArrayList<>(n);
                    for (int i = 1; i <= n; i++) {
                        row.add(normalizeCell(rs.getObject(i)));
                    }
                    out.getRows().add(row);
                }
            }
        } catch (Exception e) {
            log.error("smart-query 执行失败 [{}]: {}", ds.getDsName(), e.getMessage());
            throw new IllegalStateException("数据查询执行失败: " + e.getMessage(), e);
        }
        return out;
    }

    /** JDBC 时间类型统一归一为可读字符串：Jackson 默认把 java.sql.Date/Timestamp 序列化为
     *  毫秒时间戳（如 1758556800000），前端表格会原样展示裸数字；日期/时间/日期时间分格式输出 */
    private static Object normalizeCell(Object v) {
        if (v instanceof java.sql.Timestamp) {
            return FMT_DATETIME.format(((java.sql.Timestamp) v).toLocalDateTime());
        }
        if (v instanceof LocalDateTime) {
            return FMT_DATETIME.format((LocalDateTime) v);
        }
        if (v instanceof java.sql.Date) {
            return FMT_DATE.format(((java.sql.Date) v).toLocalDate());
        }
        if (v instanceof LocalDate) {
            return FMT_DATE.format((LocalDate) v);
        }
        if (v instanceof java.sql.Time) {
            return FMT_TIME.format(((java.sql.Time) v).toLocalTime());
        }
        if (v instanceof LocalTime) {
            return FMT_TIME.format((LocalTime) v);
        }
        return v;
    }

    private static final DateTimeFormatter FMT_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 解析执行用数据源：默认 Agent 用默认数据源；其余按 Agent 绑定 dsName（缓存优先，
     *  未命中查 insight_agent 并缓存）；绑定缺失时兼容旧约定回落同名数据源 */
    private SysDataSource resolveDataSource(String agentCode) {
        if (SemanticLayerRegistry.isDefault(agentCode)) {
            return dataSourceCacheService.getDefault();
        }
        String dsName = agentDsCache.get(agentCode);
        if (dsName == null) {
            InsightAgent agent = insightAgentService.selectOne(
                    new QueryWrapper<InsightAgent>().eq("agent_code", agentCode));
            dsName = agent == null ? null : agent.getDsName();
            if (FuncUtil.isNotEmpty(dsName)) {
                agentDsCache.put(agentCode, dsName);
            }
        }
        if (FuncUtil.isEmpty(dsName)) {
            // 旧数据兼容：未绑定记录时按编码取同名数据源（旧版本约定）
            dsName = agentCode;
        }
        return dataSourceCacheService.getByName(dsName);
    }
}
