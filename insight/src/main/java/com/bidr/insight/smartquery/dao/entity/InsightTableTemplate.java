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
 * Title: InsightTableTemplate
 * Description: 表级资产模板（跨 Agent 复用）：某 Agent 确认过的表实体沉淀于此，
 * 其他 Agent 同数据源选到同表时骨架自动套用人工结论（角色/单位/归类/键/分区），免重复配置
 *
 * @author Sharp
 * @since 2026/8/24
 */
@ApiModel(description = "表级资产模板")
@Data
@TableName(value = "insight_table_template")
public class InsightTableTemplate {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Integer id;

    @TableField(value = "ds_name")
    @ApiModelProperty(value = "数据源名（模板身份的一半）")
    @Size(max = 100, message = "数据源名最大长度要小于 100")
    private String dsName;

    @TableField(value = "table_name")
    @ApiModelProperty(value = "表全名（db.tbl 形式，模板身份的另一半）")
    @Size(max = 200, message = "表全名最大长度要小于 200")
    private String tableName;

    @TableField(value = "entity_json")
    @ApiModelProperty(value = "已确认实体 JSON（单个 EntityDef，含字段级人工结论）")
    private String entityJson;

    @TableField(value = "source_agent")
    @ApiModelProperty(value = "最近沉淀来源 Agent 编码（追溯用）")
    @Size(max = 50, message = "来源 Agent 编码最大长度要小于 50")
    private String sourceAgent;

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
