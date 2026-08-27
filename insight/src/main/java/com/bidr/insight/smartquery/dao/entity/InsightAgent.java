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
 * Title: InsightAgent
 * Description: 智能问数 Agent 配置表：一个 Agent 绑定一个数据源与一组选中的表，
 * 语义层资产存 insight_agent_asset（草稿→发布状态机），运行期按 agentCode 隔离取用
 *
 * @author Sharp
 * @since 2026/8/18
 */
@ApiModel(description = "智能问数 Agent 配置表")
@Data
@TableName(value = "insight_agent")
public class InsightAgent {

    @TableId(value = "agent_id", type = IdType.AUTO)
    @ApiModelProperty(value = "Agent 主键")
    private Integer agentId;

    @TableField(value = "agent_code")
    @ApiModelProperty(value = "Agent 编码（唯一，semantic_query.agent 取值）")
    @Size(max = 50, message = "Agent 编码最大长度要小于 50")
    private String agentCode;

    @TableField(value = "agent_name")
    @ApiModelProperty(value = "Agent 名称")
    @Size(max = 100, message = "Agent 名称最大长度要小于 100")
    private String agentName;

    @TableField(value = "ds_name")
    @ApiModelProperty(value = "绑定的数据源名称（sys_data_source.ds_name）")
    @Size(max = 100, message = "数据源名称最大长度要小于 100")
    private String dsName;

    @TableField(value = "status")
    @ApiModelProperty(value = "状态（1=启用 0=停用），停用后问数拒绝该 Agent")
    @Size(max = 1, message = "状态最大长度要小于 1")
    private String status;

    @TableField(value = "thinking_budget")
    @ApiModelProperty(value = "思考强度（仅问数链；思考 token 上限）：空/非正=最强不限制；资产生成/评审走系统参数")
    private Integer thinkingBudget;

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

    @TableField(value = "remark")
    @ApiModelProperty(value = "备注")
    @Size(max = 500, message = "备注最大长度要小于 500")
    private String remark;
}
