package com.bidr.qcc.dto.enterprise;

import com.bidr.qcc.dto.QccReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: EnterpriseAdvancedReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 10:48
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseAdvancedReq extends QccReq {
    @ApiModelProperty("搜索关键字（企业名称、统一社会信用代码、注册号）注：社会组织、中国香港企业仅支持通过企业名称查询")
    private String keyword;
}
