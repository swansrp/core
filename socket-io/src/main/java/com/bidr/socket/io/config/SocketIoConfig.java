package com.bidr.socket.io.config;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.utils.NetUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.platform.redis.aop.publish.RedisPublishConfig;
import com.bidr.socket.io.controller.SocketIoEndpoint;
import com.bidr.socket.io.dao.po.key.SocketIoRedisKey;
import com.bidr.socket.io.dao.po.msg.TopicChatMessage;
import com.bidr.socket.io.service.socket.ReceiveMessageService;
import com.corundumstudio.socketio.AuthorizationResult;
import com.corundumstudio.socketio.SocketConfig;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.annotation.SpringAnnotationScanner;
import com.corundumstudio.socketio.listener.ConnectListener;
import com.corundumstudio.socketio.listener.DisconnectListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.lang.reflect.Method;

/**
 * Title: SocketIoConfig
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/10/31 10:49
 */

@Slf4j
@Configuration
public class SocketIoConfig implements ApplicationContextAware, CommandLineRunner {

    public static final String TOKEN = "token";
    public static final String OPERATOR = "operator";
    public static final String RECEIVE_MSG_HANDLER = "receiveSubscribeMessage";
    private static final String PROJECT_NAME_PROPERTY = "my.project.name";
    private static ApplicationContext applicationContextInstance;
    @Value("${my.chat.server.port:}")
    private Integer port;
    @Resource
    private RedisPublishConfig redisPublishConfig;
    @Resource
    private TokenService tokenService;

    public static String buildChatMessageTopic() {
        return applicationContextInstance.getEnvironment().getProperty(PROJECT_NAME_PROPERTY) + "-" +
                SocketIoRedisKey.SOCKET_IO_MSG_TOPIC_KEY;
    }

    @Bean
    public SpringAnnotationScanner springAnnotationScanner(SocketIOServer socketServer) {
        return new SpringAnnotationScanner(socketServer);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        applicationContextInstance = applicationContext;
    }

    @Bean
    public SocketIOServer socketIOServer() {
        SocketConfig socketConfig = new SocketConfig();
        socketConfig.setReuseAddress(true);
        com.corundumstudio.socketio.Configuration config = new com.corundumstudio.socketio.Configuration();
        String localIp = NetUtil.getLocalIp();
        log.info("socket io 服务启动 {}:{}", localIp, port);
        config.setHostname(localIp);
        config.setPort(port == null ? 0 : port);
        config.setSocketConfig(socketConfig);
        config.setExceptionListener(new SockIoExceptionListener());
        config.setRandomSession(true);

        //该处可以用来进行身份验证
        config.setAuthorizationListener(data -> {
            String token = data.getSingleUrlParam(TOKEN);
            TokenInfo tokenInfo = AuthTokenUtil.resolveToken(token);
            String operator = data.getSingleUrlParam(OPERATOR);
            log.info("token:{}, operator:{}", token, operator);
            tokenService.verifyToken(tokenInfo);
            return new AuthorizationResult(true, tokenService.getTokenValue(tokenInfo));
        });

        SocketIOServer socketIOServer = new SocketIOServer(config);
        socketIOServer.addConnectListener(onConnect());
        socketIOServer.addDisconnectListener(onDisconnected());
        return socketIOServer;
    }

    private ConnectListener onConnect() {
        return client -> {
            Object delegate = applicationContextInstance.getBean(SocketIoEndpoint.class);
            Method method = ReflectionUtil.getMethod(SocketIoEndpoint.class, "onConnect", SocketIOClient.class);
            ReflectionUtil.invoke(delegate, method, client);
        };
    }

    private DisconnectListener onDisconnected() {
        return client -> {
            Object delegate = applicationContextInstance.getBean(SocketIoEndpoint.class);
            Method method = ReflectionUtil.getMethod(SocketIoEndpoint.class, "onDisconnect", SocketIOClient.class);
            ReflectionUtil.invoke(delegate, method, client);
        };
    }

    @Override
    public void run(String... args) throws Exception {
        if (port != null) {
            Object delegate = applicationContextInstance.getBean(ReceiveMessageService.class);
            Method method = ReflectionUtil.getMethod(ReceiveMessageService.class, RECEIVE_MSG_HANDLER,
                    TopicChatMessage.class);
            redisPublishConfig.registerPublish(buildChatMessageTopic(), delegate, method, TopicChatMessage.class);
            socketIOServer().start();
        }
    }
}
