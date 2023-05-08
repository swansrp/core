package com.bidr.authorization.constants.common;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Title: ClientType.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019-7-28 21:18
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "CLIENT_TYPE_DICT", remark = "客户端类型")
public enum ClientType implements Dict {
    /**
     *
     */
    WEB("0", "网页端"),
    PUBLIC("2", "微信公众号"),
    WECHAT("3", "微信小程序"),
    APP("1", "APP"),
    PLATFORM("8", "对接平台"),
    IOT("9", "IOT设备");


    private final String value;
    private final String label;
    private final HashMap<Object, Enum<?>> map = new HashMap<>();


}
