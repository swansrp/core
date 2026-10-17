package com.bidr.socket.io.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: MessageTypeDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/31 10:57
 */

@Getter
@RequiredArgsConstructor
@MetaDict(value = "CHAT_MESSAGE_DICT", remark = "聊天消息类型")
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
public enum MessageTypeDict implements Dict {

    TEXT("0", "文字"),
    IMAGE("1", "图片"),
    AUDIO("2", "语音"),
    VIDEO("3", "视频"),
    HTML("4", "富文本"),
    SYS("5", "系统消息");

    private final String value;
    private final String label;
}