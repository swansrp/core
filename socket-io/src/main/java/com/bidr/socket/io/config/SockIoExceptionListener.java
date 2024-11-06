package com.bidr.socket.io.config;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.ExceptionListenerAdapter;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import static com.bidr.socket.io.config.SocketIoConfig.TOKEN;

/**
 * Title: SockIoExceptionListener
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/31 10:49
 */

@Slf4j
public class SockIoExceptionListener extends ExceptionListenerAdapter {

    @Override
    public boolean exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        log.error("发生错误 :{}", e.getMessage());
        return true;
    }

    @Override
    public void onAuthException(Throwable throwable, SocketIOClient socketIOClient) {
        log.info("token:{} 验证不通过", socketIOClient.getHandshakeData().getSingleUrlParam(TOKEN));
    }

    @Override
    public void onPingException(Exception e, SocketIOClient client) {
        log.error("客户端 {}====轮询异常 {}", client.getNamespace(), e.getMessage());
    }
}
