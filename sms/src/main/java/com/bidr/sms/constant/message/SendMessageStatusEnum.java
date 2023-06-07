package com.bidr.sms.constant.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: SendMessageStatusEnum
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 14:54
 */
@Getter
@AllArgsConstructor
public enum SendMessageStatusEnum {
    /**
     *
     */
    REQUEST(0, "申请"),
    SUCCESS(1, "发送成功"),
    SENDING(2, "发送中"),
    MOCK(3, "本地发送成功"),
    FAIL(99, "发送失败");

    private final Integer status;
    private final String result;

}
