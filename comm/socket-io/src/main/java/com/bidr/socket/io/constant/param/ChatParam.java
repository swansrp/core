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
@MetaParam("即时聊天")
@RequiredArgsConstructor
@SuppressWarnings("AlibabaEnumConstantsMustHaveComment")
public enum ChatParam implements Param {

    CHAT_MESSAGE_EXPIRED_TIME("聊天消息存储过期时间", "604800",
            "【出厂默认即可】聊天消息在服务端存储的过期时间（秒），默认 7 天；过期后历史消息不再可拉"),

    CHAT_HISTORY_EXPIRED_TIME("登录信息存储过期时间", "604800",
            "【出厂默认即可】即时聊天的登录态存储过期时间（秒），默认 7 天"),

    CHAT_MESSAGE_MANUAL_DELIVERED_ACK("消息送达手动ack开关", "1",
            "【出厂默认即可】1＝消息送达由客户端手动 ack，0＝自动确认；仅在弱网/多端同步出问题时调整");

    private final String title;
    private final String defaultValue;
    private final String remark;
}