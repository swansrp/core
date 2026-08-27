package com.bidr.insight.smartquery.validate.conflict;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: ConfigCheckResolution
 * Description: 配置自查裁决请求（前端逐条点一条传一条，无批量）：
 * type 路由到对应规则回写；adopt 把结论写进配置，keep 只记裁决不动配置，两者都落经验标记；
 * snake_case 与 ConfigCheckFinding 出参对称（domain_key）
 *
 * @author Sharp
 * @since 2026/8/25
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ConfigCheckResolution {

    /** 裁决动作：采纳建议（写配置） */
    public static final String ACTION_ADOPT = "adopt";

    /** 裁决动作：维持原值（不改配置，仅记裁决经验防重复提） */
    public static final String ACTION_KEEP = "keep";

    /** 疑点类型（路由到对应规则） */
    private String type;

    private String entity;

    private String field;

    /** 码值域类专属：value-domains 的 domains key */
    private String domainKey;

    /** adopt / keep */
    private String action;

    /** 单位类采纳的目标单位（adopt 时） */
    private String unit;

    /** 缺码类本次裁决涉及的码值（adopt 补登记 / keep 记入忽略清单） */
    private List<String> codes;
}
