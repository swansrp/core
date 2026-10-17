package com.bidr.agent.console.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: ModelUsageSummary
 * Description: 用量统计的一行聚合读数（M14 棒5）：按「日 × 用途档 × agent × 模型」分组。
 *
 * <p>core 只出<b>用量事实</b>的聚合，不含单价与金额——那是上层运营政策（单价会变、各项目不同）。
 * {@code usageMissing} 是本桶里"网关没回 usage"的行数：它非零时，同一行的 token 合计就是<b>下限</b>
 * 而不是真值（SSE 链路尤其如此），读统计的人必须能看到这个缺口，否则会把少计当成少用。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "模型用量聚合读数（按日 × 用途档 × agent × 模型）")
@Data
public class ModelUsageSummary {

    @ApiModelProperty(value = "统计日（取 call_at 的日期部分）")
    private Date statDate;

    @ApiModelProperty(value = "用途档（AGENT/VISION/DEFAULT/NODE…）")
    private String purposeType;

    @ApiModelProperty(value = "agent 运行时注册键（该组调用没有会话归属时为空）")
    private String agentKey;

    @ApiModelProperty(value = "模型 id")
    private String modelName;

    @ApiModelProperty(value = "调用次数")
    private Long callCount;

    @ApiModelProperty(value = "输入 token 合计")
    private Long promptTokens;

    @ApiModelProperty(value = "输出 token 合计")
    private Long completionTokens;

    @ApiModelProperty(value = "思考 token 合计")
    private Long reasoningTokens;

    @ApiModelProperty(value = "总 token 合计")
    private Long totalTokens;

    @ApiModelProperty(value = "耗时合计（毫秒）")
    private Long costMs;

    @ApiModelProperty(value = "本桶拿不到 usage 的行数（非零＝token 合计只是下限）")
    private Long usageMissing;
}
