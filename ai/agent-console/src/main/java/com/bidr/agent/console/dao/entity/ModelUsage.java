package com.bidr.agent.console.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: ModelUsage
 * Description: 模型调用用量流水行（M14 棒5）：一次模型调用一行，只记用量事实。
 *
 * <p>token 三栏与 {@code tool_count} 都允许 NULL，且 <b>NULL 不等于 0</b>——
 * NULL＝这一路网关没回该字段（SSE 尤其常见：本客户端没在请求体追加 include_usage），
 * 0＝真的一个没有。把"没拿到"写成 0，按人/按空间汇总就会系统性少计。</p>
 *
 * <p>{@code keyFp} 是 sha256 摘要的前 16 位，明文 key 不进本类、不进库、不进日志。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "模型调用用量流水（一次调用一行）")
@Data
@TableName(value = "sys_model_usage")
public class ModelUsage {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @TableField(value = "call_kind")
    @ApiModelProperty(value = "调用载体：SYNC/SSE/VISION")
    private String callKind;

    @TableField(value = "purpose_type")
    @ApiModelProperty(value = "用途档（provider 的 purpose，如 AGENT/VISION/DEFAULT/NODE）")
    private String purposeType;

    @TableField(value = "model_name")
    @ApiModelProperty(value = "本次实际生效的模型 id")
    private String modelName;

    @TableField(value = "key_fp")
    @ApiModelProperty(value = "模型 key 指纹（sha256 hex 前 16 位；无明文 key）")
    private String keyFp;

    @TableField(value = "session_id")
    @ApiModelProperty(value = "会话标识（有则填）")
    private String sessionId;

    @TableField(value = "agent_key")
    @ApiModelProperty(value = "agent 运行时注册键（有则填）")
    private String agentKey;

    @TableField(value = "asked_by")
    @ApiModelProperty(value = "发起人（有则填）")
    private String askedBy;

    @TableField(value = "prompt_tokens")
    @ApiModelProperty(value = "输入 token（NULL＝网关没回 usage）")
    private Integer promptTokens;

    @TableField(value = "completion_tokens")
    @ApiModelProperty(value = "输出 token（NULL 同上）")
    private Integer completionTokens;

    @TableField(value = "reasoning_tokens")
    @ApiModelProperty(value = "思考 token（网关未给该字段＝NULL）")
    private Integer reasoningTokens;

    @TableField(value = "total_tokens")
    @ApiModelProperty(value = "总 token（只取网关原值，缺失不相加）")
    private Integer totalTokens;

    @TableField(value = "tool_count")
    @ApiModelProperty(value = "本轮工具只数（视觉链路不适用＝NULL）")
    private Integer toolCount;

    @TableField(value = "finish_reason")
    @ApiModelProperty(value = "网关 finish_reason 原值")
    private String finishReason;

    @TableField(value = "cost_ms")
    @ApiModelProperty(value = "本次调用耗时（毫秒）")
    private Long costMs;

    @TableField(value = "call_at")
    @ApiModelProperty(value = "调用收口时刻（＝用量发生时刻）")
    private Date callAt;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建人（落库线程无登录上下文，恒空）")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间（＝落库插入时刻）")
    private Date createAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性（保留列随规范；流水不删）")
    private String valid;
}
