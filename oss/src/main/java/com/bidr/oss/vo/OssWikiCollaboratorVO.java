package com.bidr.oss.vo;

import com.bidr.authorization.dao.entity.AcUser;
import com.diboot.core.binding.annotation.BindField;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Wiki协作者VO
 *
 * @author sharp
 * @since 2025-12-12
 */
@ApiModel(description = "Wiki协作者")
@Data
public class OssWikiCollaboratorVO {

    @ApiModelProperty(value = "页面ID")
    private Long pageId;

    @ApiModelProperty(value = "用户ID")
    private String userId;

    @BindField(entity = AcUser.class, field = "name", condition = "this.userId=customer_number")
    @ApiModelProperty(value = "用户名称")
    private String userName;

    @ApiModelProperty(value = "权限类型: 1-只读, 2-编辑")
    private String permission;

    @ApiModelProperty(value = "状态: 0-待审批, 1-已通过, 2-已拒绝")
    private String status;

    @ApiModelProperty(value = "申请说明")
    private String requestMsg;

    @ApiModelProperty(value = "创建时间")
    private Date createAt;

    @ApiModelProperty(value = "更新时间")
    private Date updateAt;
}
