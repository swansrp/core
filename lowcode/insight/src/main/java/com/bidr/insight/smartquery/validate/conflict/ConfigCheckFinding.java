package com.bidr.insight.smartquery.validate.conflict;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * Title: ConfigCheckFinding
 * Description: 配置自查疑点（规则探测产物，前端逐条裁决的单元）：
 * 类型 + 定位（实体/表/列/域键）+ 现状 + 建议 + 证据；原样序列化给前端
 *
 * @author Sharp
 * @since 2026/8/25
 */
@Data
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class ConfigCheckFinding {

    /** 疑点类型（与规则 type() 对应，裁决回写时路由） */
    private String type;

    private String entity;

    private String table;

    private String field;

    /** 码值域类专属：value-domains 的 domains key */
    private String domainKey;

    /** 现状（已配单位/「未填写」/已登记码值数） */
    private String current;

    /** 建议（注释单位 / 缺失码值预览） */
    private String suggestion;

    /** 证据（注释原文 / 采样说明） */
    private String evidence;

    /** 码值域类完整缺码清单（建议文案仅预览） */
    private List<String> missingCodes;
}
