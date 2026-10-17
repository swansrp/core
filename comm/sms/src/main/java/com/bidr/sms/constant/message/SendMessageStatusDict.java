package com.bidr.sms.constant.message;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: SendMessageStatusDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 14:54
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "SEND_MESSAGE_STATUS_DICT", remark = "短信发送状态字典")
public enum SendMessageStatusDict implements Dict {
    /**
     *
     */
    REQUEST(0, "申请"),
    SUCCESS(1, "发送成功"),
    SENDING(2, "发送中"),
    MOCK(3, "本地发送成功"),
    FAIL(99, "发送失败");

    private final Integer value;
    private final String label;

}
