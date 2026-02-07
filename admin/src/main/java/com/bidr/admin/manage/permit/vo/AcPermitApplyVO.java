package com.bidr.admin.manage.permit.vo;

import com.bidr.admin.config.PortalDictField;
import com.bidr.admin.config.PortalDisplayNoneField;
import com.bidr.admin.config.PortalEntityField;
import com.bidr.admin.config.PortalIdField;
import com.bidr.admin.vo.BaseVO;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.constant.dict.common.ApprovalDict;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

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
     * 用户名称
     */
    @ApiModelProperty(value = "用户名称")
    @PortalEntityField(entity = AcUser.class, field = "name", alias = "ac_user")
    private String userName;

    /**
     * 菜单ID（权限ID）
     */
    @PortalDisplayNoneField
    @ApiModelProperty(value = "菜单ID")
    private Long menuId;

    @PortalEntityField(entity= AcMenu.class, alias = "ac_menu", field = "title")
    @ApiModelProperty(value = "权限名称")
    private String menuName;

    /**
     * 审批状态（0-未提交，1-待审核，2-未通过，3-已通过）
     */
    @PortalDictField(ApprovalDict.class)
    @ApiModelProperty(value = "状态")
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
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @ApiModelProperty(value = "审批时间")
    private Date auditAt;
}