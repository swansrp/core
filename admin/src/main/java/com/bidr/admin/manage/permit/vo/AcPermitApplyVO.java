package com.bidr.admin.manage.permit.vo;

import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 权限申请表VO - 前端交互
 *
 * @author sharp
 * @since 2026-02-06
 */
@ApiModel(description = "权限申请表")
@Data
@EqualsAndHashCode(callSuper = true)
public class AcPermitApplyVO extends BaseVO {
    /**
     * 主键ID
     */
    @PortalIdField
    @ApiModelProperty(value = "主键ID")
    private Long id;

    /**
     * 用户编码
     */
    @ApiModelProperty(value = "用户编码")
    private String customerNumber;

    /**
     * 菜单ID（权限ID）
     */
    @ApiModelProperty(value = "菜单ID（权限ID）")
    private Long menuId;

    @ApiModelProperty(value = "权限名称")
    private String menuName;

    /**
     * 审批状态（0-未提交，1-待审核，2-未通过，3-已通过）
     */
    @ApiModelProperty(value = "审批状态（0-未提交，1-待审核，2-未通过，3-已通过）")
    private String status;

    /**
     * 申请理由
     */
    @ApiModelProperty(value = "申请理由")
    private String reason;

    /**
     * 审批意见
     */
    @ApiModelProperty(value = "审批意见")
    private String auditRemark;

    /**
     * 审批人
     */
    @ApiModelProperty(value = "审批人")
    private String auditBy;

    /**
     * 审批时间
     */
    @ApiModelProperty(value = "审批时间")
    private Date auditAt;

    /**
     * 创建者
     */
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    /**
     * 更新者
     */
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    /**
     * 有效性
     */
    @ApiModelProperty(value = "有效性")
    private String valid;
}