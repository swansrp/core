package com.bidr.insight.smartquery.validate.conflict;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.ColumnConventions;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ConfigCheckContext
 * Description: 配置自查共享探针（规则按需取用，不重复实现）：列注释读取、实体→表映射、
 * 码值采样，全部惰性加载+缓存（多规则共用一次读库）；自查失败静默回落不阻断
 *
 * @author Sharp
 * @since 2026/8/25
 */
@Slf4j
public class ConfigCheckContext {

    /** 码值探测上限：DISTINCT 结果超过即视为非枚举列跳过（与骨架采样同口径） */
    public static final int DOMAIN_PROBE_LIMIT = 51;

    private final Connection conn;
    private final List<EntityDef> entities;
    private final Map<String, ValueDomainDef> domains;

    /** 惰性缓存：表全名 → 列 → 注释 */
    private Map<String, Map<String, String>> comments;
    /** 惰性缓存：实体名 → 表全名 */
    private Map<String, String> tableByEntity;

    public ConfigCheckContext(Connection conn, List<EntityDef> entities, Map<String, ValueDomainDef> domains) {
        this.conn = conn;
        this.entities = entities == null ? new ArrayList<>() : entities;
        this.domains = domains == null ? new LinkedHashMap<>() : domains;
    }

    public List<EntityDef> entities() {
        return entities;
    }

    public Map<String, ValueDomainDef> domains() {
        return domains;
    }

    /** 单表列注释（注释是单位/码值的证据源，读物理表不信草稿展示名）；读取失败该表出空 map */
    public Map<String, String> commentsOf(String table) {
        ensureComments();
        return comments.getOrDefault(table, Collections.emptyMap());
    }

    /** 测试注入口：预置注释结果跳过读库 */
    public void useComments(Map<String, Map<String, String>> preset) {
        this.comments = preset;
    }

    /** 实体名 → 表全名（域检测定位采样表用）；无对应实体返回 null */
    public String tableOfEntity(String name) {
        if (tableByEntity == null) {
            tableByEntity = new HashMap<>();
            for (EntityDef e : entities) {
                if (FuncUtil.isNotEmpty(e.getName()) && FuncUtil.isNotEmpty(e.getTable())) {
                    tableByEntity.putIfAbsent(e.getName(), e.getTable());
                }
            }
        }
        return name == null ? null : tableByEntity.get(name);
    }

    /** 采样单列去重取值：超探测上限视为非枚举返回空；采样失败（宽表/视图不可查）返回空不阻断 */
    public List<String> sampleDistinct(String fullName, String col) {
        String sql = "SELECT DISTINCT `" + col + "` AS v FROM " + fullName +
                " WHERE `" + col + "` IS NOT NULL AND `" + col + "` <> '' LIMIT " + (DOMAIN_PROBE_LIMIT + 1);
        List<String> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(rs.getString("v"));
            }
        } catch (Exception e) {
            log.warn("配置自查码值采样失败 {}.{}: {}", fullName, col, e.getMessage());
            return Collections.emptyList();
        }
        return rows.size() > DOMAIN_PROBE_LIMIT ? Collections.emptyList() : rows;
    }

    /** 按 schema 批量读列注释（一次读库全规则共用）；单 schema 读失败只影响该批表 */
    private void ensureComments() {
        if (comments != null) {
            return;
        }
        comments = new HashMap<>();
        Map<String, List<String>> tablesBySchema = new LinkedHashMap<>();
        for (EntityDef e : entities) {
            String[] split = FuncUtil.isEmpty(e.getTable()) ? null : ColumnConventions.splitTableName(e.getTable());
            if (split != null) {
                tablesBySchema.computeIfAbsent(split[0], k -> new ArrayList<>()).add(split[1]);
            }
        }
        for (Map.Entry<String, List<String>> entry : tablesBySchema.entrySet()) {
            StringBuilder in = new StringBuilder();
            for (int i = 0; i < entry.getValue().size(); i++) {
                in.append(i > 0 ? ",?" : "?");
            }
            String sql = "SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT FROM INFORMATION_SCHEMA.COLUMNS " +
                    "WHERE TABLE_SCHEMA = ? AND TABLE_NAME IN (" + in + ")";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, entry.getKey());
                for (int i = 0; i < entry.getValue().size(); i++) {
                    ps.setString(i + 2, entry.getValue().get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        comments.computeIfAbsent(entry.getKey() + "." + rs.getString("TABLE_NAME"),
                                        k -> new HashMap<>())
                                .put(rs.getString("COLUMN_NAME"), rs.getString("COLUMN_COMMENT"));
                    }
                }
            } catch (Exception e) {
                log.warn("配置自查列注释读取失败（该批表跳过）: schema={}, {}", entry.getKey(), e.getMessage());
            }
        }
    }
}
