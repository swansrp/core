package com.bidr.insight.chatbi.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: ChatBiFlow
 * Description: 智能问数流程编排——路由/问答两条链路的 DAG 定义存库，
 * 前端画布可编辑（结点/连线/提示词模板），库中无记录时引擎回落代码内置默认链
 *
 * @author Sharp
 * @since 2026/8/15
 */
@ApiModel(description = "智能问数流程编排")
@Data
@AccountContextFill
@TableName(value = "insight_chatbi_flow")
public class ChatBiFlow {
    /**
     * 流程标识（route-看板路由 ask-看板问答）
     */
    @TableId(value = "flow_key", type = IdType.INPUT)
    @ApiModelProperty(value = "流程标识")
    private String flowKey;

    /**
     * 流程名称
     */
    @TableField(value = "name")
    @ApiModelProperty(value = "流程名称")
    private String name;

    /**
     * DAG 定义（nodes+edges JSON，含提示词模板与结点坐标）
     */
    @TableField(value = "graph")
    @ApiModelProperty(value = "DAG 定义 JSON")
    private String graph;

    /**
     * 创建者
     */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
