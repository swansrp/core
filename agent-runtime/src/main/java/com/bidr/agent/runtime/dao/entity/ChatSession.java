package com.bidr.agent.runtime.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * AI对话会话映射（业务归属与业务绑定）
 *
 * @author sharp
 */
@ApiModel(description = "AI对话会话映射（业务归属与业务绑定）")
@Data
@AccountContextFill
@TableName(value = "sys_agent_session")
public class ChatSession {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Long id;

    @TableField(value = "session_id")
    @ApiModelProperty(value = "平台会话ID（Agent Runtime 的 session_id）")
    private String sessionId;

    @TableField(value = "agent_code")
    @ApiModelProperty(value = "平台 Agent 编码（会话绑定，平台侧创建即冻结）")
    private String agentCode;

    @TableField(value = "operator")
    @ApiModelProperty(value = "归属人（登录账号）：会话归属强校验与列表过滤依据")
    private String operator;

    @TableField(value = "group_key")
    @ApiModelProperty(value = "业务分组键（随建会话提交平台，平台侧会话列表按它过滤）")
    private String groupKey;

    @TableField(value = "business_type")
    @ApiModelProperty(value = "业务类型（业务绑定维度，如 project/contract）")
    private String businessType;

    @TableField(value = "business_id")
    @ApiModelProperty(value = "业务主键（业务绑定维度，如项目ID）")
    private String businessId;

    @TableField(value = "title")
    @ApiModelProperty(value = "会话标题（本地维护：平台 name 创建即冻结）")
    private String title;

    @TableField(value = "last_active_at")
    @ApiModelProperty(value = "最后活跃时间（列表排序用）")
    private Date lastActiveAt;

    @TableField(value = "message_count")
    @ApiModelProperty(value = "会话消息数（每轮 user+assistant 计 2，列表展示用）")
    private Integer messageCount;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "是否有效：1有效 0无效（会话删除或新对话解绑）")
    private Integer valid;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建人")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新人")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

}
