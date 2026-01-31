package com.bidr.authorization.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bidr.authorization.mybatis.anno.AccountContextFill;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import lombok.Data;

/**
 * 权限申请表
 *
 * @author sharp
 */
@ApiModel(description = "权限申请表")
@Data
@AccountContextFill
@TableName(value = "ac_permit_apply")
public class AcPermitApply {
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "ID")
    private Long id;

    @TableField(value = "user_id")
    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @TableField(value = "menu_id")
    @ApiModelProperty(value = "菜单ID（权限ID）")
    private Long menuId;

    @TableField(value = "status")
    @ApiModelProperty(value = "审批状态（0-未提交，1-待审核，2-未通过，3-已通过）")
    private String status;

    @TableField(value = "reason")
    @ApiModelProperty(value = "申请理由")
    private String reason;

    @TableField(value = "audit_remark")
    @ApiModelProperty(value = "审批意见")
    private String auditRemark;

    @TableField(value = "audit_by")
    @ApiModelProperty(value = "审批人")
    private String auditBy;

    @TableField(value = "audit_at")
    @ApiModelProperty(value = "审批时间")
    private Date auditAt;

    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    @TableField(value = "create_at", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @TableField(value = "update_at", fill = FieldFill.INSERT_UPDATE)
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @TableField(value = "`valid`")
    @ApiModelProperty(value = "有效性")
    private String valid;
}
