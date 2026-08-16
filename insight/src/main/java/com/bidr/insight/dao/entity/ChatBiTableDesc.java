package com.bidr.insight.dao.entity;

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
 * Title: ChatBiTableDesc
 * Description: 智能问数看板业务描述——外挂在 insight 侧的语义运营数据，
 * table_code 存 portalName（sys_portal.name）关联看板视图，供大模型在全局模式下路由选择看板
 *
 * @author Sharp
 * @since 2026/8/15
 */
@ApiModel(description = "智能问数看板业务描述")
@Data
@AccountContextFill
@TableName(value = "insight_chatbi_table_desc")
public class ChatBiTableDesc {
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 看板名（sys_portal.name，即路由候选的 tableId）
     */
    @TableField(value = "table_code")
    @ApiModelProperty(value = "看板名（portalName）")
    private String tableCode;

    /**
     * 业务描述（供大模型路由选择看板）
     */
    @TableField(value = "description")
    @ApiModelProperty(value = "业务描述")
    private String description;

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
