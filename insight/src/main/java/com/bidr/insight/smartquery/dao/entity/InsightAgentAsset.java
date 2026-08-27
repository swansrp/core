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
 * Title: InsightAgentAsset
 * Description: Agent 语义层资产存储：每个 (agentCode, assetType) 一条记录，content 为
 * 对应 JSON 全文（entities/metrics/dimensions/relations/value-domains/concepts 六类）。
 * 生效与否看 insight_agent.asset_status（草稿/已发布状态机），发布后经
 * AgentAssetCacheService 刷新进入运行期内存
 *
 * @author Sharp
 * @since 2026/8/18
 */
@ApiModel(description = "Agent 语义层资产存储表")
@Data
@TableName(value = "insight_agent_asset")
public class InsightAgentAsset {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Integer id;

    @TableField(value = "agent_code")
    @ApiModelProperty(value = "Agent 编码")
    @Size(max = 50, message = "Agent 编码最大长度要小于 50")
    private String agentCode;

    @TableField(value = "asset_type")
    @ApiModelProperty(value = "资产类型（entities/metrics/dimensions/relations/value-domains/concepts）")
    @Size(max = 30, message = "资产类型最大长度要小于 30")
    private String assetType;

    @TableField(value = "content")
    @ApiModelProperty(value = "草稿 JSON 全文（管理页编辑/提案合并落这里）")
    private String content;

    @TableField(value = "published_content")
    @ApiModelProperty(value = "发布快照（运行期缓存加载列；发布时由 content 拷入，改稿不触碰）")
    private String publishedContent;

    @TableField(value = "status")
    @ApiModelProperty(value = "状态（0=草稿 1=已发布），发布时整 Agent 的六类资产一起置 1")
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
