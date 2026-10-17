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
 * Title: ChatBiSensitiveColumn
 * Description: 智能问数敏感列配置——按看板登记绝不外传大模型的列（值域/批量取值不进提示词）。
 * 锚点用 property 不用列 id：同一逻辑列按角色存多副本 id 各不相同，property 是语义目录/
 * chartSpec 条件/数据行三处共用的唯一逻辑标识（portal 模式=sys_portal_column.property，DATASET 模式=columnAlias）
 *
 * @author Sharp
 * @since 2026/8/16
 */
@ApiModel(description = "智能问数敏感列配置")
@Data
@AccountContextFill
@TableName(value = "insight_chatbi_sensitive_column")
public class ChatBiSensitiveColumn {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 看板名（sys_portal.name，与语义目录 tableId 同键）
     */
    @TableField(value = "table_code")
    @ApiModelProperty(value = "看板名（portalName）")
    private String tableCode;

    /**
     * 敏感列属性名（portal 模式=column.property，DATASET 模式=columnAlias）
     */
    @TableField(value = "column_property")
    @ApiModelProperty(value = "敏感列属性名")
    private String columnProperty;

    /**
     * 显示名快照（审计用，展示以列清单实时数据为准）
     */
    @TableField(value = "column_label")
    @ApiModelProperty(value = "显示名快照")
    private String columnLabel;

    /**
     * 配对替换列属性名（可空；如 项目名称→项目编号，供大模型跨轮/批量子集查询与回显翻译）
     */
    @TableField(value = "replace_property")
    @ApiModelProperty(value = "配对替换列属性名")
    private String replaceProperty;

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
