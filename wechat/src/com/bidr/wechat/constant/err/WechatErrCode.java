package com.bidr.wechat.constant.err;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: ErrCodeWechat
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/25 7:39
 */
@AllArgsConstructor
public enum WechatErrCode implements ErrCode {

    /**
     *
     */
    WECHAT_SERVER_ERROR("9001", "微信服务返回异常%s,%s", ErrCodeLevel.DEBUG.getValue());

    @Getter
    @Setter
    private String errCode;
    @Getter
    @Setter
    private String errMsg;
    @Getter
    @Setter
    private String errLevel;


}
