package com.bidr.notification;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthLogin;
import com.bidr.authorization.holder.AccountContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 站内信 SSE 流端点（M21；铃铛实时推送，替代轮询）：登录人维度长连接，
 * 收件后由 {@link NotificationSseHub} 按 target_user 定向广播 {@code notification} 事件。
 * 鉴权沿 M18 形态：前端 fetch 手动带 Bearer（EventSource 不支持自定义头）。
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Api(tags = "站内信")
@Auth(AuthLogin.class)
@RestController
@RequestMapping(path = "/web/api/notification")
public class NotificationStreamController {

    private final NotificationSseHub hub;

    public NotificationStreamController(NotificationSseHub hub) {
        this.hub = hub;
    }

    /** 登录人长连接（0L＝不超时；断线靠 15s 心跳侦测） */
    @ApiOperation(value = "站内信事件流", notes = "SSE；事件 notification＝有新消息，connected＝通道就绪")
    @RequestMapping(value = "/stream", method = RequestMethod.GET, produces = "text/event-stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        hub.register(AccountContext.getOperator(), emitter);
        return emitter;
    }
}
