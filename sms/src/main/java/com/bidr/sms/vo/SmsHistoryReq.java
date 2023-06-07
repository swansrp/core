package com.bidr.sms.vo;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

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
}
