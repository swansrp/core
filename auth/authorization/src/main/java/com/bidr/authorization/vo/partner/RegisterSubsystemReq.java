package com.bidr.authorization.vo.partner;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Size;

/**
 * Title: RegisterSubsystemReq
 * Description: 渠道注册子系统请求体＝渠道备注＋子系统登记字段。
 * 一个事务内先建 ac_partner（appKey/appSecret 服务端生成）再插 ac_subsystem（partner_id=新渠道 id）。
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Data
@ApiModel(description = "渠道注册子系统申请")
public class RegisterSubsystemReq {

    @ApiModelProperty(value = "渠道备注（渠道名）")
    @Size(max = 50, message = "备注最大长度要小于 50")
    private String remark;

    @ApiModelProperty(value = "应用编码（业务主键）")
    @Size(max = 64, message = "应用编码最大长度要小于 64")
    private String appCode;

    @ApiModelProperty(value = "子系统显示名")
    @Size(max = 100, message = "子系统显示名最大长度要小于 100")
    private String name;

    @ApiModelProperty(value = "图标")
    @Size(max = 200, message = "图标最大长度要小于 200")
    private String icon;

    @ApiModelProperty(value = "入口形态（iframe/新窗口/同页）")
    @Size(max = 32, message = "入口形态最大长度要小于 32")
    private String entryType;

    @ApiModelProperty(value = "入口地址")
    @Size(max = 500, message = "入口地址最大长度要小于 500")
    private String entryUrl;

    @ApiModelProperty(value = "子系统后台地址（管理入口）")
    @Size(max = 500, message = "子系统后台地址最大长度要小于 500")
    private String adminUrl;

    @ApiModelProperty(value = "说明")
    @Size(max = 500, message = "说明最大长度要小于 500")
    private String description;
}
