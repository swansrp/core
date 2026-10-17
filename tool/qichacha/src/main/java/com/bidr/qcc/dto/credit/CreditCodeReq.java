package com.bidr.qcc.dto.credit;

import com.bidr.qcc.dto.QccReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: CreditCodeReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:14
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CreditCodeReq extends QccReq {
    @ApiModelProperty("查询关键字（公司名称、注册号）")
    private String keyWord;
}
