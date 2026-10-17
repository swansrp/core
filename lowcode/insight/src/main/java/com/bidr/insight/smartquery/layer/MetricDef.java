package com.bidr.insight.smartquery.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: MetricDef
 * Description: 语义层指标定义（metrics.json 单项）。当前 POC SQL 生成器仅支持
 * atomic（formula 直接聚合）；derived/composite 只做元数据完整性校验
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricDef {

    private String name;

    private String displayName;

    /** atomic / derived / composite */
    private String type;

    /** 聚合公式，列引用为 db.tbl.col 三段式 */
    private String formula;

    /** atomic 指标源表：db.tbl */
    private String sourceTable;

    /** composite 指标源表列表 */
    private List<String> sourceTables;

    /** 指标支持的维度白名单 */
    private List<String> supportedDimensions;

    /** 是否已认证（false 时结果需标注，§6.1.2） */
    private Boolean certified;

    /** derived 指标依赖的指标名 */
    private List<String> dependsOn;

    /** 口径时间轴字段 */
    private String timeField;

    /** certified 缺省 true（与 Python m.get("certified", True) 一致） */
    public boolean certifiedOrDefault() {
        return certified == null || certified;
    }
}
