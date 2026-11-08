package com.bidr.authorization.vo.subsystem;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: PartnerOptionRes
 * Description: 应用管理的可绑定渠道下拉项。
 * <p>🔴 绝不含 appSecret——partner 查询面密钥只在注册/新建时一次性展示，任何列表/下拉不得带出
 * （投影四字段在 AdminSubsystemService.partnerOptions 收口）。</p>
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Data
public class PartnerOptionRes {

    @ApiModelProperty(value = "id")
    private Long id;

    @ApiModelProperty(value = "应用的唯一标识key")
    private String appKey;

    @ApiModelProperty(value = "备注（渠道名）")
    private String remark;

    @ApiModelProperty(value = "有效性")
    private Integer status;
}
