package com.bidr.authorization.vo.permit;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 权限申请VO
 *
 * @author sharp
 */
@Data
public class PermitApplyVO {
    @ApiModelProperty(value = "审批状态(0-未提交,1-待审核,2-未通过,3-已通过)")
    private String status;

    @ApiModelProperty(value = "审批意见")
    private String auditRemark;
}
