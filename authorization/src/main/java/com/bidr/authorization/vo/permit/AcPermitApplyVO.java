package com.bidr.authorization.vo.permit;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * 权限申请VO
 *
 * @author sharp
 */
@ApiModel(description = "权限申请")
@Data
public class AcPermitApplyVO {
    @ApiModelProperty(value = "ID")
    private Long id;

    @ApiModelProperty(value = "用户ID")
    private Long userId;

    @ApiModelProperty(value = "用户名")
    private String userName;

    @ApiModelProperty(value = "菜单ID(权限ID)")
    private Long menuId;

    @ApiModelProperty(value = "菜单名称")
    private String menuTitle;

    @ApiModelProperty(value = "审批状态(0-未提交,1-待审核,2-未通过,3-已通过)")
    private String status;

    @ApiModelProperty(value = "申请理由")
    private String reason;

    @ApiModelProperty(value = "审批意见")
    private String auditRemark;

    @ApiModelProperty(value = "审批人")
    private String auditBy;

    @ApiModelProperty(value = "审批时间")
    private Date auditAt;

    @ApiModelProperty(value = "创建者")
    private String createBy;

    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @ApiModelProperty(value = "更新者")
    private String updateBy;

    @ApiModelProperty(value = "更新时间")
    private Date updateAt;

    @ApiModelProperty(value = "有效性")
    private String valid;
}
