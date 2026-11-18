package com.bidr.agent.capability.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Title: AgentHealthStreamController
 * Description: Agent 能力探活健康度的 SSE 流端点——前端徽标实时亮/灰的订阅口：
 * 建连即挂 {@link AgentHealthSseHub} 全局集合，探活状态变化时收 {@code health}
 * 事件（data=快照 JSON）。照站内信 {@code NotificationStreamController} 形态的
 * broadcast 简化版：<b>无 receiver 维度</b>（健康度全员一致，不取登录人），
 * 鉴权由 {@code /web/api/agent/**} 既有拦截链管（未登录拿不到流，照
 * {@code AgentSessionController} 同款先例——平台 /web/api 拦截链，不加注解）。
 * agent-capability 内首个 controller：@RestController 扫描拾取
 * （epc 扫描根 com.bidr，llm 会话 controller 同款先例）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@RestController
@RequestMapping(path = "/web/api/agent/health")
public class AgentHealthStreamController {

    private final AgentHealthSseHub hub;

    public AgentHealthStreamController(AgentHealthSseHub hub) {
        this.hub = hub;
    }

    /** 健康度事件流（0L＝不超时；断线靠 15s 心跳侦测；事件 health＝探活快照变化，
     *  connected＝通道就绪帧 data=ok） */
    @GetMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        hub.register(emitter);
        return emitter;
    }
}
