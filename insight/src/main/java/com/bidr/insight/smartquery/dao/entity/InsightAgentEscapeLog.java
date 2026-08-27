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
 * Title: InsightAgentEscapeLog
 * Description: SQL 兜底通道命中台账：每次兜底成功作答记一条（question+sql），
 * 跨部署持久积累。结晶机制的原始信号源——高频重复出现的问题/SQL 形态
 * 即下一个引擎能力候选（对照准入标准：高频+口径稳定+可写确定性测试）
 *
 * @author Sharp
 * @since 2026/8/23
 */
@ApiModel(description = "Agent SQL 兜底通道命中台账")
@Data
@TableName(value = "insight_agent_escape_log")
public class InsightAgentEscapeLog {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键")
    private Integer id;

    @TableField(value = "agent_code")
    @ApiModelProperty(value = "Agent 编码")
    @Size(max = 50, message = "Agent 编码最大长度要小于 50")
    private String agentCode;

    @TableField(value = "question")
    @ApiModelProperty(value = "用户原问题（兜底触发当次的完整问题文本）")
    private String question;

    @TableField(value = "sql_text")
    @ApiModelProperty(value = "兜底实际执行的 SQL（经只读守卫验证后的文本）")
    private String sqlText;

    @TableField(value = "note")
    @ApiModelProperty(value = "口径说明（LLM note 字段，可空）")
    private String note;

    @TableField(value = "create_by")
    @ApiModelProperty(value = "创建者（问数发起用户，框架自动填充）")
    private Long createBy;

    @TableField(value = "create_at")
    @ApiModelProperty(value = "创建时间（框架按字段名自动填充）")
    private Date createAt;
}
