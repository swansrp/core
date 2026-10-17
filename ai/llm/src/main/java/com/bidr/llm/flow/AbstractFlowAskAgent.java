package com.bidr.llm.flow;

import com.bidr.llm.sse.SseEventSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Title: AbstractFlowAskAgent
 * Description: flow 型 agent SSE 执行的模板基类——固化发起样板（SSE 连接创建/上下文组装/
 * 引擎调用/发起失败收口），业务子类只填三个钩子：{@link #flowKey} 定链、{@link #fillContext}
 * 注入业务变量与前置副作用、{@link #onStartFailed} 失败业务收口。后续 flow 执行入口
 * 一律继承本类，禁止再手拼 emitter/sender/context 样板。
 * <p>
 * 收口契约（final 禁覆写）：发起即失败时先回调业务收口钩子，再统一下发 error 事件并关闭连接；
 * 正常路径的 done/error 由链上结点直推，模板不干预。
 *
 * @param <REQ> 业务提问请求类型
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public abstract class AbstractFlowAskAgent<REQ> {

    /**
     * flow 编排执行引擎
     */
    protected final FlowEngine flowEngine;

    protected AbstractFlowAskAgent(FlowEngine flowEngine) {
        this.flowEngine = flowEngine;
    }

    /**
     * 执行的链路标识（FlowDefinitionProvider#flowKey）
     */
    protected abstract String flowKey();

    /**
     * 业务上下文注入：变量/历史/访问人写入 ctx，前置副作用（对话落库、conv 事件等）一并完成。
     * 异常向外抛出走 {@link #onStartFailed} + error 事件收口
     */
    protected abstract void fillContext(FlowContext context, SseEventSender sender, REQ req);

    /**
     * 发起即失败的业务收口钩子（如补写错误回复、msgid 先于 error 下发）；默认空。
     * 钩子自身异常被吞掉只记日志，不阻断 error 事件下发
     */
    protected void onStartFailed(FlowContext context, SseEventSender sender, REQ req, Exception error) {
    }

    /**
     * 当前访问人（写 ctx 供轨迹/对话按人隔离）：默认无。
     * 注意 SSE 挂起后回调线程读不到请求线程上下文，业务须在发起线程先取
     */
    protected String operator() {
        return null;
    }

    /**
     * 发起失败 error 事件文案：默认透传异常消息，无消息回落通用提示
     */
    protected String failureMessage(Exception error) {
        return error.getMessage() == null ? "流程执行发起失败" : error.getMessage();
    }

    /**
     * 模板执行体：final 禁覆写——建连/组装/执行/失败收口的样板与收口契约由基类保证
     */
    public final SseEmitter ask(REQ req) {
        // 超时 0 不限时，由模型侧的生成结束/失败来关闭连接
        SseEmitter emitter = new SseEmitter(0L);
        SseEventSender sender = new SseEventSender(emitter);
        FlowContext context = new FlowContext(sender);
        context.setOperator(operator());
        try {
            fillContext(context, sender, req);
            flowEngine.execute(flowKey(), context);
        } catch (Exception e) {
            log.warn("flow 执行发起失败, flowKey={}", flowKey(), e);
            try {
                onStartFailed(context, sender, req, e);
            } catch (Exception hookError) {
                log.warn("flow 发起失败收口钩子异常, flowKey={}", flowKey(), hookError);
            }
            sender.send(SseEventSender.EVENT_ERROR, failureMessage(e));
            sender.complete();
        }
        return emitter;
    }
}
