package com.bidr.wechat.constant.err;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: ErrCodeWechat
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/25 7:39
 */
@Getter
@RequiredArgsConstructor
public enum WechatErrCode implements ErrCode {

    /**
     *
     */
    WECHAT_SERVER_ERROR(9001, "微信服务返回异常%s,%s", ErrCodeLevel.DEBUG.getValue());

    private final Integer errCode;
    private final String errMsg;
    private final String errLevel;


}
