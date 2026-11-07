package com.bidr.authorization.vo.subsystem;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * Title: SubsystemReq
 * Description: 应用管理 新增请求体（更新走通用 /update，app_code 不可改）。
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Data
@ApiModel(description = "子系统登记申请")
public class SubsystemReq {

    @ApiModelProperty(value = "应用编码（业务主键）")
    @Size(max = 64, message = "应用编码最大长度要小于 64")
    private String appCode;

    @ApiModelProperty(value = "子系统显示名")
    @Size(max = 100, message = "子系统显示名最大长度要小于 100")
    private String name;

    @ApiModelProperty(value = "图标")
    @Size(max = 200, message = "图标最大长度要小于 200")
    private String icon;

    @ApiModelProperty(value = "门户排序")
    private Integer orderNo;

    @ApiModelProperty(value = "入口形态（iframe/新窗口/同页）")
    @Size(max = 32, message = "入口形态最大长度要小于 32")
    private String entryType;

    @ApiModelProperty(value = "入口地址")
    @Size(max = 500, message = "入口地址最大长度要小于 500")
    private String entryUrl;

    @ApiModelProperty(value = "归属partner（ac_partner.id）")
    private Long partnerId;

    @ApiModelProperty(value = "是否对所有租户可见（1=公开目录/0=按开通与授权判定）")
    @Size(max = 1, message = "是否对所有租户可见最大长度要小于 1")
    private String visibleToAllTenant;

    @ApiModelProperty(value = "子系统后台地址（管理入口）")
    @Size(max = 500, message = "子系统后台地址最大长度要小于 500")
    private String adminUrl;

    @ApiModelProperty(value = "状态（1=启用）")
    @Size(max = 20, message = "状态最大长度要小于 20")
    private String status;

    @ApiModelProperty(value = "说明")
    @Size(max = 500, message = "说明最大长度要小于 500")
    private String description;
}
