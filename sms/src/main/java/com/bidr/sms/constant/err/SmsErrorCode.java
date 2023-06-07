package com.bidr.sms.constant.err;

import com.bidr.kernel.constant.err.ErrCode;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: SmsErrorCode
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 16:18
 */
@Getter
@AllArgsConstructor
public enum SmsErrorCode implements ErrCode {

    /**
     *
     */
    APPLY_FAILED(80, "[%s]短信模板申请失败"),
    SIGN_NOT_REGISTER(81, "[%s]签名没有申请通过"),
    QUERY_SIGN_FAILED(82, "[%s]签名查询失败"),
    SMS_TYPE_ALREADY_EXISTED(83, "[%s]短信类型已存在")

    ;


    private final Integer errCode;
    private final String errMsg;
}
