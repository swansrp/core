package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单区块实例
 *
 * @author sharp
 */
@ApiModel(description = "表单区块实例")
@Data
@AccountContextFill
@TableName(value = "form_data_section_instance")
public class FormDataSectionInstance {
    /**
     * 表单实例 ID
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @ApiModelProperty(value = "表单实例 ID")
    private String id;

    /**
     * 上报历史 id
     */
    @TableField(value = "history_id")
    @ApiModelProperty(value = "上报历史 id")
    private String historyId;

    /**
     * 区块 id
     */
    @TableField(value = "section_id")
    @ApiModelProperty(value = "区块 id")
    private Long sectionId;

    /**
     * 版本号
     */
    @TableField(value = "version_no")
    @ApiModelProperty(value = "版本号")
    private Integer versionNo;

    /**
     * 备注/说明
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注/说明")
    private String remark;

    /**
     * 上报内容 JSON
     */
    @TableField(value = "form_content")
    @ApiModelProperty(value = "上报内容 JSON")
    private String formContent;

    /**
     * 提交人
     */
    @TableField(value = "submitted_by")
    @ApiModelProperty(value = "提交人")
    private String submittedBy;

    /**
     * 提交时间
     */
    @TableField(value = "submitted_at")
    @ApiModelProperty(value = "提交时间")
    private Date submittedAt;

    /**
     * 审核人
     */
    @TableField(value = "confirm_by")
    @ApiModelProperty(value = "审核人")
    private String confirmBy;

    /**
     * 审核时间
     */
    @TableField(value = "confirm_at")
    @ApiModelProperty(value = "审核时间")
    private Date confirmAt;

    /**
     * 状态：1=已提交，0=草稿，2=审核中，3=退回
     */
    @TableField(value = "confirm_status")
    @ApiModelProperty(value = "状态：1=已提交，0=草稿，2=审核中，3=退回")
    private String confirmStatus;

    /**
     * 审核意见
     */
    @TableField(value = "confirm_comment")
    @ApiModelProperty(value = "审核意见")
    private String confirmComment;

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
