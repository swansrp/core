package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.layer.EntityDef;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Title: TableProfiler
* Description: 选表画像采集（骨架阶段确定性预探索，纯代码零 LLM）：逐表跑总行数/分区值域/
 * 键唯一性两三条聚合 SQL，渲染为画像文本注入生成提示词【表画像】段——替代 LLM 开局
 * 必做的表形态探测（数行数/探分区/判快照重复/取最新分区值），实证可省 8~10 个探索轮。
 * 分区列识别按数仓命名规范（粒度粗到细）：字段含 dy（年快照分区）、dm（月快照分区）
  * 或 dd（日快照分区）；键列优先实体
 * primaryKey，缺省启发式取首个 *id 字段（画像文本标注启发式来源，LLM 认为不可靠可自行
 * 复核）。单表画像失败仅告警跳过不阻断生成链（画像缺失退化为无画像，行为与旧版一致）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
public class TableProfiler {

    /** 分区值全列展示上限：超出只展示 min~max + 最近几个分区（防日快照宽值域刷屏上下文） */
    static final int PARTITION_DETAIL_LIMIT = 12;

    /** 宽值域时展示的最近分区个数（最新分区最常用：快照对齐写法的锚点） */
    static final int RECENT_PARTITIONS_SHOWN = 5;

    /**
     * 逐表画像（每表 1~2 条聚合 SQL；单表异常仅告警跳过）。
     * 返回每表一行画像文本，顺序与入参实体一致；无分区无键的空表也产出占位行
     */
    public List<String> profile(Connection conn, List<EntityDef> entities) {
        List<String> lines = new ArrayList<>();
        for (EntityDef entity : entities) {
            try {
                String line = profileOne(conn, entity);
                if (line != null && !line.isEmpty()) {
                    lines.add(line);
                }
            } catch (Exception e) {
                log.warn("表 {} 画像采集失败（忽略，生成链继续）: {}", entity.getTable(), e.getMessage());
            }
        }
        return lines;
    }

    /** 单表画像：分区分布查询 + 总览查询（键 distinct 需全表口径，不能按分区加总），渲染一行 */
    private String profileOne(Connection conn, EntityDef entity) throws Exception {
        String table = entity.getTable();
        String partition = detectPartition(entity);
        boolean heuristicKey;
        String key = detectKey(entity);
        heuristicKey = key != null && (entity.getPrimaryKey() == null || entity.getPrimaryKey().isEmpty());

        List<Object[]> partRows = new ArrayList<>();
        long total = 0;
        long totalKeys = -1;
        if (partition != null) {
            String sql = "SELECT " + partition + ", COUNT(*) AS cnt"
                    + (key != null ? ", COUNT(DISTINCT " + key + ") AS key_cnt" : "")
                    + " FROM " + table + " GROUP BY " + partition + " ORDER BY " + partition;
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (key != null) {
                        partRows.add(new Object[]{rs.getString(1), rs.getLong(2), rs.getLong(3)});
                    } else {
                        partRows.add(new Object[]{rs.getString(1), rs.getLong(2)});
                    }
                }
            }
            if (key == null) {
                for (Object[] row : partRows) {
                    total += (Long) row[1];
                }
            }
        }
        if (key != null) {
            // 总览查询（有键必查）：全局行数 + 键全局 distinct——跨分区重复判定需全表口径，不能按分区加总
            String sql = "SELECT COUNT(*), COUNT(DISTINCT " + key + ") FROM " + table;
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total = rs.getLong(1);
                    totalKeys = rs.getLong(2);
                }
            }
        } else if (partition == null) {
            String sql = "SELECT COUNT(*) FROM " + table;
            try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    total = rs.getLong(1);
                }
            }
        }
        return render(table, partition, key, heuristicKey, total, totalKeys, partRows);
    }

    /** 分区列识别：实体字段优先（骨架预选/人工确认都落这里），回落启发式粗到细：
     *  dy（年快照）→ dm（月快照）→ dd（日快照）；均无返回 null */
    public static String detectPartition(EntityDef entity) {
        if (entity.getPartitionColumn() != null && !entity.getPartitionColumn().isEmpty()) {
            return entity.getPartitionColumn();
        }
        for (String candidate : new String[]{"dy", "dm", "dd"}) {
            for (EntityDef.EntityFieldDef f : entity.getFields()) {
                if (candidate.equalsIgnoreCase(f.getName())) {
                    return f.getName();
                }
            }
        }
        return null;
    }

    /** 键列识别：实体 primaryKey 优先（数仓表常无 PRI），缺省启发式取首个 *id 结尾字段 */
    public static String detectKey(EntityDef entity) {
        if (entity.getPrimaryKey() != null && !entity.getPrimaryKey().isEmpty()) {
            return entity.getPrimaryKey().get(0);
        }
        for (EntityDef.EntityFieldDef f : entity.getFields()) {
            String name = f.getName();
            if (name != null && name.toLowerCase().endsWith("id")) {
                return name;
            }
        }
        return null;
    }

    /**
     * 画像渲染（纯函数可测）：一行文本承载总行数/分区值域/键全局与分区内唯一性/最新分区值，
     * 附确定性结构推断（跨分区重复 → 快照型计数纪律）；宽值域截断只展示最近几个分区
     */
    public static String render(String table, String partition, String key, boolean heuristicKey,
                               long total, long totalKeys, List<Object[]> partRows) {
        StringBuilder sb = new StringBuilder("- ").append(table).append("：");
        if (partition == null) {
            if (key == null) {
                sb.append("无分区列，无可用键列，共 ").append(total).append(" 行");
            } else {
                sb.append("无分区列，共 ").append(total).append(" 行");
                if (totalKeys >= 0) {
                    sb.append("，键 ").append(key).append(heuristicKey ? "（启发式）" : "")
                            .append(" 全局 distinct ").append(totalKeys)
                            .append(total == totalKeys ? "（全局唯一）" : "（全局存在重复）");
                }
            }
            return sb.toString();
        }
        sb.append("分区列 ").append(partition)
                .append("（").append(partRows.size()).append(" 个值：")
                .append(partRows.get(0)[0]).append("~").append(partRows.get(partRows.size() - 1)[0]).append("）");
        if (key != null && totalKeys >= 0) {
            sb.append("，总 ").append(total).append(" 行，键 ").append(key)
                    .append(heuristicKey ? "（启发式）" : "").append(" 全局 distinct ").append(totalKeys);
            if (totalKeys < total) {
                sb.append(" → 键跨分区重复（快照型表：按业务键计数/去重须限定单一分区）");
            } else if (totalKeys == total) {
                sb.append("（全局唯一）");
            }
            boolean uniqueInPartition = true;
            for (Object[] row : partRows) {
                if (((Long) row[1]).longValue() != ((Long) row[2]).longValue()) {
                    uniqueInPartition = false;
                    break;
                }
            }
            if (uniqueInPartition) {
                sb.append("，各分区内键唯一");
            }
        } else {
            sb.append("，总 ").append(total).append(" 行");
        }
        sb.append("；");
        if (partRows.size() <= PARTITION_DETAIL_LIMIT) {
            sb.append("各分区 行/键：");
            for (int i = 0; i < partRows.size(); i++) {
                if (i > 0) {
                    sb.append("，");
                }
                appendPartition(sb, partRows.get(i));
            }
        } else {
            sb.append("分区值过多（").append(partRows.size()).append(" 个）只列最近 ")
                    .append(RECENT_PARTITIONS_SHOWN).append(" 个（行/键）：");
            for (int i = Math.max(0, partRows.size() - RECENT_PARTITIONS_SHOWN); i < partRows.size(); i++) {
                if (i > Math.max(0, partRows.size() - RECENT_PARTITIONS_SHOWN)) {
                    sb.append("，");
                }
                appendPartition(sb, partRows.get(i));
            }
        }
        return sb.toString();
    }

    /** 单分区明细：2023 行 6077/键 6077（无键列时只写行数） */
    private static void appendPartition(StringBuilder sb, Object[] row) {
        sb.append(row[0]).append(" 行 ").append(row[1]);
        if (row.length > 2) {
            sb.append("/键 ").append(row[2]);
        }
    }
}
