package com.sharp.authorization.constants.common;

import com.sharp.kernel.constant.dict.Dict;
import com.sharp.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: ClientTypeConst.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-7-28 21:18
 */
@AllArgsConstructor
@MetaDict("CLIENT_TYPE_DICT")
public enum ClientTypeConst implements Dict {
    /**
     *
     */
    WEB("0", "网页端"),
    PUBLIC("2", "微信公众号"),
    WECHAT("3", "微信小程序"),
    APP("1", "APP"),
    PLATFORM("8", "对接平台"),
    IOT("9", "IOT设备");

    @Getter
    @Setter
    private String value;
    @Getter
    @Setter
    private String label;

}
