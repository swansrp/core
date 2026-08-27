package com.bidr.insight.smartquery.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: ConceptDef
 * Description: 语义层业务概念定义（concepts.json 单项）：业务口语术语到
 * 维度+码值条件的实体映射（expands_to 单维度单值），问数解析时展开进 filters
 * （filters 中禁止直接引用概念名，§6.6.2）。目录工具 conceptDetail 按需展开供给模型
 *
 * @author Sharp
 * @since 2026/8/23
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConceptDef {

    private String name;

    private List<String> aliases;

    /** 展开条件所属实体（语义层实体名） */
    private String entity;

    /** 展开定义：维度 + 运算符 + 存储值 */
    private ExpandsTo expandsTo;

    private String note;

    /**
     * Title: ExpandsTo
     * Description: 概念展开条件（单维度单值，如 manage_mode = '0'）
     */
    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExpandsTo {

        private String dimension;

        private String operator;

        private String value;
    }
}
