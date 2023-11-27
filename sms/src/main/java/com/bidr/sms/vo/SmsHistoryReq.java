package com.bidr.sms.vo;

import com.bidr.kernel.vo.query.QueryReqVO;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * Title: SmsHistoryReq
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 10:37
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SmsHistoryReq extends QueryReqVO {
    @ApiModelProperty("手机号码")
    private String phoneNumber;

    @ApiModelProperty("平台")
    private String platform;

    @ApiModelProperty("模板id")
    private String templateCode;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date queryStartAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date queryEndAt;
}
