package com.bidr.socket.io.config;

import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.listener.ExceptionListenerAdapter;
import io.netty.channel.ChannelHandlerContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

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
    public void onEventException(Exception e, List<Object> data, SocketIOClient client) {
        log.error("onEventException", e);
    }

    @Override
    public void onDisconnectException(Exception e, SocketIOClient client) {
        log.error("onDisconnectException", e);
    }

    @Override
    public void onConnectException(Exception e, SocketIOClient client) {
        log.error("onConnectException", e);
    }


    @Override
    public void onPongException(Exception e, SocketIOClient client) {
        log.error("onPongException", e);
    }


    @Override
    public boolean exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
        log.error("发生错误 :{}", e.getMessage());
        return true;
    }

    @Override
    public void onAuthException(Throwable throwable, SocketIOClient socketioClient) {
        log.error("onAuthException", throwable);
    }

    @Override
    public void onPingException(Exception e, SocketIOClient client) {
        log.error("onPingException", e);
    }
}
