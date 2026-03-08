package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 属性分组实例表
 *
 * @author sharp
 */
@ApiModel(description = "属性分组实例表")
@Data
@AccountContextFill
@TableName(value = "form_data_group_instance")
public class FormDataGroupInstance {
    /**
     * 主键 ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    @ApiModelProperty(value = "主键 ID")
    private String id;

    /**
     * 上传历史 ID
     */
    @TableField(value = "history_id")
    @ApiModelProperty(value = "上传历史 ID")
    private String historyId;

    /**
     * 区块实例 ID
     */
    @TableField(value = "section_instance_id")
    @ApiModelProperty(value = "区块实例 ID")
    private String sectionInstanceId;

    /**
     * 分组配置 ID
     */
    @TableField(value = "group_id")
    @ApiModelProperty(value = "分组配置 ID")
    private Long groupId;

    /**
     * 行索引（多组子表场景）
     */
    @TableField(value = "row_index")
    @ApiModelProperty(value = "行索引（多组子表场景）")
    private Integer rowIndex;

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
