package com.bidr.agent.console.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: AgentDefinition
 * Description: agent 资产定义行（M14 棒6）：一行一个 {@code agentKey}，五列各存一份 JSON 数组。
 *
 * <p>五个勾选列在本类里<b>一律是 {@code String}（原始 JSON 文本），不是 List</b>——
 * 三态判据要求"NULL"与"空数组"是两个不同的值，而 MyBatis 把 {@code List} 映射回来时
 * 这两者都会变成同一个空集合。解析只在 {@code SelectionColumn} 一处做，实体不翻译。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@ApiModel(description = "agent 资产定义（勾选数据，一行一个 agent）")
@Data
@TableName(value = "sys_agent_definition")
public class AgentDefinition {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @TableField(value = "agent_key")
    @ApiModelProperty(value = "agent 运行时注册键")
    private String agentKey;

    @TableField(value = "capability_packages")
    @ApiModelProperty(value = "能力包勾选（JSON 数组原文；NULL＝未启用＝全部放行）")
    private String capabilityPackages;

    @TableField(value = "system_skills")
    @ApiModelProperty(value = "系统技能包勾选（JSON 数组原文）")
    private String systemSkills;

    @TableField(value = "external_capabilities")
    @ApiModelProperty(value = "外部能力勾选（JSON 数组原文）")
    private String externalCapabilities;

    @TableField(value = "model_purpose")
    @ApiModelProperty(value = "模型用途档指定（JSON 数组原文）")
    private String modelPurpose;

    @TableField(value = "prompt_segments")
    @ApiModelProperty(value = "提示词段勾选（JSON 数组原文）")
    private String promptSegments;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建人")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "最后改勾选的人")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性（保留列随规范；本表无删除口）")
    private String valid;
}
