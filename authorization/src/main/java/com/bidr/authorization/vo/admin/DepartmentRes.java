package com.bidr.authorization.vo.admin;

import com.bidr.platform.config.portal.PortalEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.Date;

/**
 * Title: DepartmentRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/12/28 21:22
 */
@Data
@PortalEntity("department")
public class DepartmentRes {

    @ApiModelProperty(value = "部门id")
    @Size(max = 20, message = "部门id最大长度要小于 20")
    @NotBlank(message = "部门id不能为空")
    private String deptId;

    @ApiModelProperty(value = "父部门id")
    @Size(max = 20, message = "父部门id最大长度要小于 20")
    private String pid;

    @ApiModelProperty(value = "祖父id")
    @Size(max = 20, message = "祖父id最大长度要小于 20")
    private String grandId;

    @ApiModelProperty(value = "部门名称")
    @Size(max = 30, message = "部门名称最大长度要小于 30")
    private String name;

    @ApiModelProperty(value = "简称")
    @Size(max = 50, message = "简称最大长度要小于 50")
    private String abbreviate;

    @ApiModelProperty(value = "建立时间")
    private Date foundedTime;

    @ApiModelProperty(value = "类别")
    @Size(max = 20, message = "类别最大长度要小于 20")
    private String category;

    @ApiModelProperty(value = "类型")
    @Size(max = 20, message = "类型最大长度要小于 20")
    private String type;

    @ApiModelProperty(value = "职能")
    @Size(max = 20, message = "职能最大长度要小于 20")
    private String function;

    @ApiModelProperty(value = "负责人")
    @Size(max = 20, message = "负责人最大长度要小于 20")
    private String leader;

    @ApiModelProperty(value = "联系电话")
    @Size(max = 11, message = "联系电话最大长度要小于 11")
    private String contact;

    @ApiModelProperty(value = "地址")
    @Size(max = 50, message = "地址最大长度要小于 50")
    private String address;

    @ApiModelProperty(value = "部门状态")
    private Integer status;

    @ApiModelProperty(value = "显示顺序")
    private Integer showOrder;
}
