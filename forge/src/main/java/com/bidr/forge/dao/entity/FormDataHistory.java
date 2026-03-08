package com.bidr.forge.dao.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import com.bidr.kernel.mybatis.anno.AutoInsert;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 表单填写历史
 *
 * @author sharp
 */
@ApiModel(description = "表单填写历史")
@Data
@AccountContextFill
@TableName(value = "form_data_history")
public class FormDataHistory {
    /**
     * 上报历史 ID
     */
    @TableId(value = "id", type = IdType.INPUT)
    @AutoInsert(seq = "FORM_DATA_HISTORY_ID_SEQ")
    @ApiModelProperty(value = "上报历史 ID")
    private String id;

    /**
     * 表单 ID
     */
    @TableField(value = "form_id")
    @ApiModelProperty(value = "表单 ID")
    private String formId;

    /**
     * 批号
     */
    @TableField(value = "version_no")
    @ApiModelProperty(value = "批号")
    private String versionNo;

    /**
     * 总体状态：0 草稿 1 提交 2 审核中 3 通过 4 退回
     */
    @TableField(value = "status")
    @ApiModelProperty(value = "总体状态：0 草稿 1 提交 2 审核中 3 通过 4 退回")
    private String status;

    /**
     * 备注/说明
     */
    @TableField(value = "remark")
    @ApiModelProperty(value = "备注/说明")
    private String remark;

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
     * 审批人
     */
    @TableField(value = "confirm_by")
    @ApiModelProperty(value = "审批人")
    private String confirmBy;

    /**
     * 审批时间
     */
    @TableField(value = "confirm_at")
    @ApiModelProperty(value = "审批时间")
    private Date confirmAt;

    /**
     * 审批理由
     */
    @TableField(value = "confirm_reason")
    @ApiModelProperty(value = "审批理由")
    private String confirmReason;

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

    /**
     * 有效性
     */
    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
