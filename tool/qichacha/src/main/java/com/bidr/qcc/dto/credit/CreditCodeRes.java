package com.bidr.qcc.dto.credit;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: CreditCodeReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:14
 */
@Data
public class CreditCodeRes {
    @ApiModelProperty("企业名称")
    @JsonProperty("Name")
    private String name;

    @ApiModelProperty("统一社会信用代码（纳税人识别号）")
    @JsonProperty("CreditCode")
    private String creditCode;

    @ApiModelProperty("企业类型")
    @JsonProperty("EconKind")
    private String econKind;

    @ApiModelProperty("企业状态")
    @JsonProperty("Status")
    private String status;

    @ApiModelProperty("地址")
    @JsonProperty("Address")
    private String address;

    @ApiModelProperty("联系电话")
    @JsonProperty("Tel")
    private String tel;

    @ApiModelProperty("开户行")
    @JsonProperty("Bank")
    private String bank;

    @ApiModelProperty("开户行账号")
    @JsonProperty("BankAccount")
    private String bankAccount;
}
