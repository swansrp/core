package com.bidr.insight.smartquery.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: DimensionDef
 * Description: 语义层维度定义（dimensions.json 单项）：
 * expression 为 db.tbl.col 三段式列引用；码值域绑定不设维度级字段（恒与域键=维度名约定重复），
 * 由 SemanticLayer.domainOfDim 按「实体字段声明回退 + 域键=维度名约定」双路解析
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class DimensionDef {

    private String name;

    private String displayName;

    /** 列引用：db.tbl.col */
    private String expression;

    /** 是否已认证（骨架重建时已认证项保留不被覆盖） */
    private Boolean certified;

    /** 维度类型：time 等 */
    private String type;

    /** 日期粒度（year/month/quarter）：有值时 expression 仍三段式直引日期列，
     *  SQL 生成器按粒度自动包日期函数（YEAR/DATE_FORMAT/CONCAT）分组，
     *  不开放自由函数表达式——支撑「每年/每月」类分组聚合间数高频形态 */
    private String granularity;

    /** 匹配方式：默认（空）等值匹配；multi=逗号分隔多值列（如 business_types="A,B"），
     *  等值/in 过滤由 SQL 生成器改写为 FIND_IN_SET 包含匹配（实体字段 multi_value 派生而来） */
    private String match;

    /** 口语别名（业务叫法/简称，供问数检索命中；骨架阶段从列备注确定性提取） */
    private List<String> aliases;
}
