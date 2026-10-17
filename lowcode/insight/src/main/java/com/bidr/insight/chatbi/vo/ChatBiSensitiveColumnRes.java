package com.bidr.insight.chatbi.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: ChatBiSensitiveColumnRes
 * Description: 敏感列配置页的列清单条目——看板全量有效列 + 敏感标记与配对列回显
 *（数据源为全量列而非仅语义目录列：不可筛选的文本列同样可能经数据回流外泄，一并可配）
 *
 * @author Sharp
 * @since 2026/8/16
 */
@ApiModel(description = "敏感列配置页列清单条目")
@Data
public class ChatBiSensitiveColumnRes {

    @ApiModelProperty(value = "列属性名（配置锚点）")
    private String property;

    @ApiModelProperty(value = "显示名")
    private String label;

    @ApiModelProperty(value = "语义化类型（与语义目录 fieldType 同口径）")
    private String fieldType;

    @ApiModelProperty(value = "列备注（DATASET 模式 remark 原文，含值域/日期格式约定）")
    private String remark;

    @ApiModelProperty(value = "是否已配置为敏感列")
    private Boolean sensitive;

    @ApiModelProperty(value = "配对替换列属性名（未配置为 null）")
    private String replaceProperty;
}
