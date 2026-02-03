package com.bidr.qcc.dto.enterprise;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: EnterpriseReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:14
 */
@Data
public class EnterpriseRes {
    @ApiModelProperty("主键")
    @JsonProperty("KeyNo")
    private String keyNo;

    @ApiModelProperty("企业名称")
    @JsonProperty("Name")
    private String name;

    @ApiModelProperty("统一社会信用代码")
    @JsonProperty("CreditCode")
    private String creditCode;

    @ApiModelProperty("成立日期")
    @JsonProperty("StartDate")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date startDate;

    @ApiModelProperty("法定代表人姓名")
    @JsonProperty("OperName")
    private String corporateName;

    @ApiModelProperty("状态")
    @JsonProperty("Status")
    private String status;

    @ApiModelProperty("注册号")
    @JsonProperty("No")
    private String regNo;

    @ApiModelProperty("注册地址")
    @JsonProperty("Address")
    private String address;
}
