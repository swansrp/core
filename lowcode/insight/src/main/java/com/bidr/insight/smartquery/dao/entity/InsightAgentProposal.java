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
 * Title: InsightAgentProposal
 * Description: 问数资产变更建议（待审提案）：问数遇到资产缺失时 LLM 维护产出的单项资产
 * 建议（一次提问 = 一个 batch），经「变更提案」审批页合并进对应 Agent 的草稿资产或驳回；
 * 运行期问数不读本表，仅管理端审批使用
 *
 * @author Sharp
 * @since 2026/8/19
 */
@ApiModel(description = "Agent 问数资产变更建议表")
@Data
@TableName(value = "insight_agent_proposal")
public class InsightAgentProposal {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Integer id;

    @TableField(value = "agent_code")
    @ApiModelProperty(value = "Agent 编码")
    @Size(max = 50, message = "Agent 编码最大长度要小于 50")
    private String agentCode;

    @TableField(value = "batch_no")
    @ApiModelProperty(value = "批次号（一次提问维护产出一批）")
    @Size(max = 40, message = "批次号最大长度要小于 40")
    private String batchNo;

    @TableField(value = "question_text")
    @ApiModelProperty(value = "触发维护的自然语言问题")
    private String questionText;

    @TableField(value = "semantic_query")
    @ApiModelProperty(value = "基于建议资产命中的 semantic_query 原文")
    private String semanticQuery;

    @TableField(value = "asset_type")
    @ApiModelProperty(value = "资产类型（metrics/dimensions/relations/value-domains/concepts/sensitive-fields）")
    @Size(max = 30, message = "资产类型最大长度要小于 30")
    private String assetType;

    @TableField(value = "item_key")
    @ApiModelProperty(value = "资产项标识（指标/维度/关系名、码值域键、概念名等）")
    @Size(max = 100, message = "资产项标识最大长度要小于 100")
    private String itemKey;

    @TableField(value = "op")
    @ApiModelProperty(value = "变更动作（add=新增 update=修改）")
    @Size(max = 10, message = "变更动作最大长度要小于 10")
    private String op;

    @TableField(value = "content")
    @ApiModelProperty(value = "单项资产 JSON 原文")
    private String content;

    @TableField(value = "reason")
    @ApiModelProperty(value = "LLM 给出的建议理由")
    private String reason;

    @TableField(value = "status")
    @ApiModelProperty(value = "状态（0=待审 1=已合并 2=已驳回）")
    @Size(max = 1, message = "状态最大长度要小于 1")
    private String status;

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
