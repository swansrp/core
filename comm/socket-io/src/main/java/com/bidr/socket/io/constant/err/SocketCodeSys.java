package com.bidr.socket.io.constant.err;

import com.bidr.kernel.constant.err.ErrCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Title: SocketCodeSys
 * Description: Copyright: Copyright (c) 2019 Company: bidr
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */
@Getter
@RequiredArgsConstructor
public enum SocketCodeSys implements ErrCode {
    // 连接已断开
    SOCKET_DISCONNECTED(10100, "连接已断开"),
    // 重复连接
    SOCKET_DUPLICATE(10101, "重复连接");

    private final Integer errCode;
    private final String errMsg;
}
