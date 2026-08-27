package com.bidr.insight.smartquery.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: EntityDef
 * Description: 语义层实体定义（entities.json 单项）：数仓表 + 主键 + 字段清单 +
 * 列表查询约束（listable/display_fields/time_field）
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class EntityDef {

    private String name;

    private String displayName;

    /** 数仓物理表：db.tbl */
    private String table;

    /** 主键字段列表（list 查询 DISTINCT 规则依据） */
    private List<String> primaryKey;

    /** 是否允许 list 查询 */
    private Boolean listable;

    /** list 查询必须携带时间窗口的字段 */
    private String timeField;

    /** list 查询缺省输出字段 */
    private List<String> displayFields;

    /** 是否已认证（骨架重建时已认证项保留不被覆盖） */
    private Boolean certified;

    /** 字段清单 */
    private List<EntityFieldDef> fields;

    /** 缺省过滤条件（涉及该实体的查询自动追加 WHERE，如启用+已审核；值来自可信资产，非用户输入） */
    private List<DefaultFilter> defaultFilters;

    /** 分区列（骨架识别+人工确认；LLM 查询该表时条件应携带该分区） */
    private String partitionColumn;

    /** 表快照类型（骨架从表名后缀识别：数仓约定 dyf=年全量/dyi=年增量/月族 dmf/dmi/无粒度 no 等）；
     *  全量表取单期、增量表跨期需累加，随实体元信息注入提示词防 LLM 算错累计数；无后缀约定为 null */
    private String snapshotType;

    /** 列配置是否已经人工确认（生成闸放行依据；旧数据缺省视为未确认） */
    private Boolean confirmed;

    /** LLM 输入切片（静态可测）：浅拷贝实体清单并剔除 disabled 字段（禁用列不参与 LLM 输入）；
     *  不修改原对象（提示词构造不得污染共享上下文）；无禁用列的实体原样保留 */
    public static List<EntityDef> forLlmInput(List<EntityDef> entities) {
        if (entities == null) {
            return new ArrayList<>();
        }
        List<EntityDef> out = new ArrayList<>(entities.size());
        for (EntityDef e : entities) {
            if (e.getFields() == null || e.getFields().stream().noneMatch(f -> Boolean.TRUE.equals(f.getDisabled()))) {
                out.add(e);
                continue;
            }
            EntityDef copy = new EntityDef();
            copy.setName(e.getName());
            copy.setDisplayName(e.getDisplayName());
            copy.setTable(e.getTable());
            copy.setPrimaryKey(e.getPrimaryKey());
            copy.setListable(e.getListable());
            copy.setTimeField(e.getTimeField());
            copy.setDisplayFields(e.getDisplayFields());
            copy.setCertified(e.getCertified());
            copy.setDefaultFilters(e.getDefaultFilters());
            copy.setPartitionColumn(e.getPartitionColumn());
            copy.setSnapshotType(e.getSnapshotType());
            copy.setConfirmed(e.getConfirmed());
            copy.setFields(e.getFields().stream()
                    .filter(f -> !Boolean.TRUE.equals(f.getDisabled()))
                    .collect(Collectors.toList()));
            out.add(copy);
        }
        return out;
    }

    /** 实体缺省过滤条件单项：field = 列值等值比较 */
    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DefaultFilter {

        private String field;

        private Object value;
    }

    /** 实体字段定义 */
    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntityFieldDef {

        private String name;

        private String displayName;

        private String type;

        /** 码值域名（引用 value-domains.json 的 domains key） */
        private String valueDomain;

        /** 敏感字段标记（如项目名称） */
        private Boolean sensitive;

        /** 敏感字段对应的脱敏编码字段 */
        private String codeField;

        /** 列角色：ignore=忽略 / dimension=维度 / metric=度量（骨架启发式预选，人工确认后采信） */
        private String role;

        /** 度量单位（仅 role=metric 有意义，如 元/万元/%/个；人工确认后 LLM 禁止再核实） */
        private String unit;

        /** 时间粒度（仅时间维度列有意义）：year/quarter/month/day */
        private String granularity;

        /** 维度归类（仅 role=dimension 有意义）：人工在实体确认页选的大类组名，
         *  保存时汇入 concepts 资产的 hierarchy 分级目录（与目录面板同源同流） */
        private String dimGroup;

        /** 多值列标记（仅 role=dimension 有意义）：逗号分隔多值 code 串（如 business_types="A,B"），
         *  派生维度带 match=multi，问数等值过滤改写为 FIND_IN_SET 包含匹配，防等值/分组失真 */
        private Boolean multiValue;

        /** 禁用列标记（人工结论）：不参与 LLM 输入（提示词实体切片/列角色段剔除），
         *  且不派生维度（问数侧不可见）；骨架重建不覆盖 */
        private Boolean disabled;

        /** 该列是否被人工修改过（区分启发式预选与人工结论，未改过的才允许骨架重建覆盖） */
        private Boolean edited;

        /** 单位已经人工裁决过（配置自查疑点逐条点过：采纳注释单位或维持原值均算）；
         *  裁决即经验：后续自查与同表复用（模板沉淀随实体携走）不再重复提单位疑点 */
        private Boolean unitVerified;
    }
}
