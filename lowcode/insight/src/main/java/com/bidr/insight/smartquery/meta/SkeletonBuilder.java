package com.bidr.insight.smartquery.meta;

import com.bidr.insight.smartquery.constant.dict.TechColumnDict;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.service.TableProfiler;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: SkeletonBuilder
 * Description: 单表骨架构建（从 SmartAgentMetaService 拆出）：读 INFORMATION_SCHEMA 列/索引元数据，
 * 产实体骨架 + 维度骨架 + 码值域（备注解析优先，其次确定性配对 GROUP BY 采样）。
 * 命名/类型约定走 ColumnConventions，备注码值解析走 CommentValueParser
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Slf4j
@Component
public class SkeletonBuilder {

    /** 码值采样上限：DISTINCT 结果不超过该值才出码值域 */
    private static final int DOMAIN_SAMPLE_LIMIT = 51;

    /** 单表 → 实体骨架 + 维度骨架 + 码值域（生成服务逐表调用，conn 由调用方管理）。
     * 码值域两条路径，备注解析优先：
     * ① 列备注解析——COLUMN_COMMENT 中的码值模式（0=启用 1=停用 等）直接出 storedAs=code 值域
     *   （portal 字典列翻译/筛选渲染的供给方），Integer 枚举列（tinyint 状态位）也借此出维度；
     * ② 确定性配对 + GROUP BY 采样——同词干编码↔名称双列（X_code ↔ X_name/_nm/_mc；无 _code 后缀时裸词干列兜底配对）
     *   采样出真实 code→label 映射；域锚定在 code 列（该列物理存码，storedAs=code），过滤可输名称自动转码、
     *   分组输出自动码转名；name 列本就存可读名称无需挂域；配不上的字段留给 LLM 推断 */
    public void buildTableAssets(Connection conn, String schema, String tbl, String tableComment,
                                  List<EntityDef> entities, List<DimensionDef> dimensions,
                                  Map<String, ValueDomainDef> domains,
                                  Set<String> entityNames, Set<String> dimensionNames) throws Exception {
        String fullName = schema + "." + tbl;
        String entityName = ColumnConventions.uniqueName(tbl, entityNames);

        EntityDef entity = new EntityDef();
        entity.setName(entityName);
        entity.setDisplayName(FuncUtil.isNotEmpty(tableComment) ? tableComment : fullName);
        entity.setTable(fullName);
        List<EntityDef.EntityFieldDef> fields = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        Map<String, String> comments = new HashMap<>();

        // 第一遍：读列元数据（配对推断需要全字段视野；备注原文留给码值解析）
        String sql = "SELECT COLUMN_NAME, COLUMN_COMMENT, DATA_TYPE, COLUMN_KEY " +
                "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tbl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("DATA_TYPE");
                    String comment = rs.getString("COLUMN_COMMENT");
                    comments.put(col, comment);

                    EntityDef.EntityFieldDef field = new EntityDef.EntityFieldDef();
                    field.setName(col);
                    field.setDisplayName(FuncUtil.isNotEmpty(comment) ? comment : col);
                    field.setType(ColumnConventions.mapType(dataType));
                    fields.add(field);
                    if ("PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY"))) {
                        primaryKeys.add(col);
                    }
                }
            }
        }
        if (fields.isEmpty()) {
            log.warn("表 {} 无列信息，跳过", fullName);
            return;
        }

        // 索引补采（STATISTICS）：COLUMN_KEY 只标唯一索引首列，复合唯一全貌要查这里；
        // 数仓表常无物理主键，有唯一索引的库（MySQL 系）借此补业务键，读失败不阻断骨架（Doris 等无索引库回落启发式）
        if (primaryKeys.isEmpty()) {
            List<List<String>> uniqueIndexes = readUniqueIndexes(conn, schema, tbl);
            if (!uniqueIndexes.isEmpty()) {
                primaryKeys.addAll(uniqueIndexes.get(0));
            }
        }

        // 确定性编码↔名称配对（同词干）
        Map<String, String> codeLabelPairs = ColumnConventions.findCodeLabelPairs(fields);
        // code-name 配对的名称侧列（瘦身：仅保留 code 侧维度挂值域，名称侧不建维度）
        Set<String> labelCols = new HashSet<>();
        codeLabelPairs.values().forEach(c -> labelCols.add(c.toLowerCase()));

        // 列角色预选（与第二遍维度派生同口径；进了维度派生的列会在那里升级为采信）；
        // 时间分区列（dy/dm/dd）角色+粒度一并钉死；unit 无可靠启发式留空待人工
        for (EntityDef.EntityFieldDef field : fields) {
            String partGran = ColumnConventions.timePartGranularity(field.getName());
            if (partGran != null) {
                field.setRole("dimension");
                field.setGranularity(partGran);
            } else {
                field.setRole(prefillRole(field.getName(), field.getType(), comments.get(field.getName()),
                        labelCols, codeLabelPairs));
            }
        }

        // 第二遍：维度骨架 + 码值域（备注解析优先，其次配对采样）
        for (EntityDef.EntityFieldDef field : fields) {
            List<ValueDomainDef.DomainValue> commentValues = CommentValueParser.parseCommentCodeVals(comments.get(field.getName()));
            boolean commentEnum = !commentValues.isEmpty();
            // 维度瘦身：技术列不建维度（技术黑名单/成对名称侧/物理 id/未配对单号工号/技术时间戳）；
            // 备注枚举列例外保留（码值语义有筛选价值）
            if (!commentEnum && TechColumnDict.isTechColumn(field.getName())) {
                continue;
            }
            if (!commentEnum && ColumnConventions.isJunkDimension(field.getName(), labelCols, codeLabelPairs)) {
                continue;
            }
            // 类型门槛：数值列不建维度，例外——时间分区列与 code-name 配对的编码侧（裸词干/整型码也是码）
            if (!commentEnum && ColumnConventions.timePartGranularity(field.getName()) == null
                    && !"String".equals(field.getType()) && !"Date".equals(field.getType())
                    && !codeLabelPairs.containsKey(field.getName())) {
                continue;
            }
            field.setRole("dimension");   // 预选项升级为采信：进了维度派生即角色钉死（人工可改）
            DimensionDef dim = new DimensionDef();
            dim.setName(ColumnConventions.uniqueDimName(field.getName(), tbl, dimensionNames));
            dim.setDisplayName(field.getDisplayName());
            dim.setExpression(fullName + "." + field.getName());
            if ("Date".equals(field.getType())) {
                dim.setType("time");
            }
            // 口语别名：从列备注主体确定性提取（括注/斜杠同义词），供问数按业务叫法检索命中；
            // 枚举值段（含分号/「数字-」取值说明）不提取，码值语义由码值域承载（findValue 按值反查）
            List<String> aliases = CommentValueParser.extractAliases(comments.get(field.getName()), field.getDisplayName());
            if (!aliases.isEmpty()) {
                dim.setAliases(aliases);
            }
            dimensions.add(dim);
            // 列级归类启发式预填（单源：dim_group 存实体字段，hierarchy 由实体派生）；
            // 携入旧列已有值则不覆盖（人工归类走 edited 标保护）
            if (FuncUtil.isEmpty(field.getDimGroup())) {
                field.setDimGroup(ConceptsSupport.matchHierarchyGroup(dim));
            }
            // 日期列确定性派生年份粒度维度（granularity=year，名称=列名_year）：「每年/按年看」
            // 类年度聚合间数高频形态直出；expression 仍三段式直引日期列，SQL 生成器按粒度包 YEAR()
            if ("Date".equals(field.getType())) {
                DimensionDef yearDim = new DimensionDef();
                yearDim.setName(ColumnConventions.uniqueDimName(field.getName() + "_year", tbl, dimensionNames));
                yearDim.setDisplayName(field.getDisplayName() + "（年）");
                yearDim.setExpression(fullName + "." + field.getName());
                yearDim.setType("time");
                yearDim.setGranularity("year");
                dimensions.add(yearDim);
            }
            if (commentEnum) {
                // 路径①：备注码值 → storedAs=code 值域（输出保留码值，portal 字典列自翻译）
                if (!domains.containsKey(dim.getName())) {
                    ValueDomainDef domain = new ValueDomainDef();
                    domain.setEntity(entityName);
                    domain.setField(field.getName());
                    domain.setStoredAs("code");
                    domain.setValues(commentValues);
                    domains.put(dim.getName(), domain);
                }
                field.setValueDomain(dim.getName());
            } else if (codeLabelPairs.containsKey(field.getName())) {
                // 路径②：同词干配对 + GROUP BY 采样（编码侧不限 String：裸词干/整型码同样采样挂域）
                String labelCol = codeLabelPairs.get(field.getName());
                if (labelCol != null
                        && samplePairedDomain(conn, fullName, entityName, field.getName(), labelCol, dim.getName(), domains)) {
                    field.setValueDomain(dim.getName());
                }
            }
        }
        entity.setFields(fields);
        entity.setPrimaryKey(primaryKeys);
        // 分区列预选（与 TableProfiler 同口径：dy/dm/dd 粗到细）；人工确认后以实体字段为准（detectPartition 优先采信）
        entity.setPartitionColumn(TableProfiler.detectPartition(entity));
        // 快照类型（表名后缀约定）：全量/增量语义注入提示词，防 LLM 算错累计数
        entity.setSnapshotType(ColumnConventions.snapshotTypeOf(tbl));
        // 可列表缺省开：事实表/单据表默认支持明细查询，超大日志/中间/敏感主数据表由管理员在资产编辑中关闭
        entity.setListable(true);
        entities.add(entity);
    }

    /** 列角色预选（与第二遍维度派生同口径）：技术列黑名单（TechColumnDict，数仓实证高频技术列）
     *  先落 ignore（含带备注枚举的技术状态如 enablestate）；再备注枚举、code-name 配对编码侧（不限类型）
     *  或非垃圾的 String/Date 列→dimension，其余（垃圾列/数值列）→ignore；仅启发式预选项，人工确认页可改 */
    private String prefillRole(String col, String type, String comment,
                               Set<String> labelCols, Map<String, String> codeLabelPairs) {
        if (TechColumnDict.isTechColumn(col)) {
            return "ignore";
        }
        boolean commentEnum = !CommentValueParser.parseCommentCodeVals(comment).isEmpty();
        if (commentEnum || codeLabelPairs.containsKey(col)
                || (("String".equals(type) || "Date".equals(type))
                && !ColumnConventions.isJunkDimension(col, labelCols, codeLabelPairs))) {
            return "dimension";
        }
        return "ignore";
    }

    /** 唯一索引清单（STATISTICS）：索引名按首见序，列按 SEQ_IN_INDEX 展开；
     *  主键索引（PRIMARY）与非唯一索引排除；读失败（无 STATISTICS 的库）返回空回落启发式 */
    private List<List<String>> readUniqueIndexes(Connection conn, String schema, String tbl) {
        Map<String, List<String>> byIndex = new LinkedHashMap<>();
        String sql = "SELECT INDEX_NAME, COLUMN_NAME FROM INFORMATION_SCHEMA.STATISTICS " +
                "WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY' " +
                "ORDER BY INDEX_NAME, SEQ_IN_INDEX";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, tbl);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    byIndex.computeIfAbsent(rs.getString("INDEX_NAME"), k -> new ArrayList<>())
                            .add(rs.getString("COLUMN_NAME"));
                }
            }
        } catch (Exception e) {
            log.debug("表 {}.{} 索引读取失败（回落启发式）: {}", schema, tbl, e.getMessage());
        }
        return new ArrayList<>(byIndex.values());
    }

    /** 配对采样落码值域：有效（非空且基数未超上限）才入 domains，返回是否生成 */
    private boolean samplePairedDomain(Connection conn, String fullName, String entityName,
                                        String codeCol, String labelCol, String domainKey,
                                        Map<String, ValueDomainDef> domains) {
        if (domains.containsKey(domainKey)) {
            return true;
        }
        List<ValueDomainDef.DomainValue> values = sampleCodeLabelPairs(conn, fullName, codeCol, labelCol);
        if (values.isEmpty()) {
            return false;
        }
        ValueDomainDef domain = new ValueDomainDef();
        domain.setEntity(entityName);
        domain.setField(codeCol);
        // 域锚定 code 列（物理存码）：storedAs 必须与锚列实际存储一致，
        // 否则过滤会把名称原样比到码列、码列分组也不会自动翻译（曾误写 label）
        domain.setStoredAs("code");
        domain.setValues(values);
        domains.put(domainKey, domain);
        return true;
    }

    /** 全表 GROUP BY code,label 采样真实映射：同 code 多 label 时保留行数最大的（ORDER BY n DESC
     * 先到先占），label 缺失回退为 code；基数达到采样上限视为非枚举列返回空 */
    public List<ValueDomainDef.DomainValue> sampleCodeLabelPairs(Connection conn, String fullName,
                                                                  String codeCol, String labelCol) {
        String sql = "SELECT `" + codeCol + "` AS c, `" + labelCol + "` AS l, COUNT(*) AS n FROM " + fullName +
                " WHERE `" + codeCol + "` IS NOT NULL AND `" + codeCol + "` <> ''" +
                " GROUP BY `" + codeCol + "`, `" + labelCol + "` ORDER BY n DESC LIMIT " + (DOMAIN_SAMPLE_LIMIT + 1);
        Map<String, ValueDomainDef.DomainValue> byCode = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String code = rs.getString("c");
                String label = rs.getString("l");
                if (FuncUtil.isEmpty(label)) {
                    label = code;
                }
                ValueDomainDef.DomainValue dv = new ValueDomainDef.DomainValue();
                dv.setCode(code);
                dv.setLabel(label);
                byCode.putIfAbsent(code, dv);
            }
        } catch (Exception e) {
            // 采样失败不阻断生成（宽表/视图可能不可查），仅记录
            log.warn("配对码值采样失败 {}.{}+{}: {}", fullName, codeCol, labelCol, e.getMessage());
            return Collections.emptyList();
        }
        if (byCode.size() >= DOMAIN_SAMPLE_LIMIT) {
            return Collections.emptyList();
        }
        return new ArrayList<>(byCode.values());
    }
}
