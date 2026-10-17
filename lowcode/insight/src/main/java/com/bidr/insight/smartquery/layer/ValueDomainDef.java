package com.bidr.insight.smartquery.layer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: ValueDomainDef
 * Description: 语义层码值域定义（value-domains.json 的 domains 项）：
 * stored_as=code 时存储码值（过滤入参 label/alias→code，结果 code→label），
 * stored_as=label 时直接存储业务标签
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ValueDomainDef {

    private String entity;

    private String field;

    /** code / label */
    private String storedAs;

    /** 是否已认证（骨架重建时已认证项保留不被覆盖） */
    private Boolean certified;

    private List<DomainValue> values;

    /** 人工裁决确认忽略的码值（配置自查探出但人拍板不登记的：脏数据/历史废弃码）；
     *  裁决即经验：后续自查采样命中此清单的码不再重复提缺码疑点 */
    private List<String> ignoredCodes;

    @Data
    @JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DomainValue {
        private String code;
        private String label;
        /** 口语别名 */
        private List<String> aliases;
    }
}
