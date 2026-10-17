package com.bidr.insight.smartquery.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: InsightAgentTable
 * Description: Agent 选表关联表：agentCode 与数据源中选中表（db.tbl 全名）的多对多记录，
 * 元数据生成器据此读 INFORMATION_SCHEMA 产出语义层资产草稿
 *
 * @author Sharp
 * @since 2026/8/18
 */
@ApiModel(description = "Agent 选表关联表")
@Data
@TableName(value = "insight_agent_table")
public class InsightAgentTable {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Integer id;

    @TableField(value = "agent_code")
    @ApiModelProperty(value = "Agent 编码")
    @Size(max = 50, message = "Agent 编码最大长度要小于 50")
    private String agentCode;

    @TableField(value = "table_name")
    @ApiModelProperty(value = "表全名（db.tbl 形式）")
    @Size(max = 200, message = "表全名最大长度要小于 200")
    private String tableName;

    @TableField(value = "table_comment")
    @ApiModelProperty(value = "表注释（选表时快照，展示用）")
    @Size(max = 500, message = "表注释最大长度要小于 500")
    private String tableComment;

    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者")
    private Long createBy;

    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间（框架按字段名自动填充）")
    private Date createAt;

    @TableField(value = "update_by")
    @ApiModelProperty(value = "更新者")
    private Long updateBy;

    @TableField(value = "update_at")
    @ApiModelProperty(value = "更新时间（框架按字段名强制刷新）")
    private Date updateAt;
}
