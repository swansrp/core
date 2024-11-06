package com.bidr.socket.io.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: ChatParam
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */
@Getter
@MetaParam
@RequiredArgsConstructor
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
public enum ChatParam implements Param {

    CHAT_MESSAGE_EXPIRED_TIME("聊天消息存储过期时间", "604800"),
    CHAT_HISTORY_EXPIRED_TIME("登录信息存储过期时间", "604800"),
    CHAT_MESSAGE_MANUAL_DELIVERED_ACK("消息送达手动ack开关", "1");

    private final String title;
    private final String defaultValue;
}