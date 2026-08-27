package com.bidr.llm.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Title: StreamingProgressChatModel
 * Description: 流式模型转同步门面 + 实时进度上报：对外暴露同步 ChatLanguageModel API，
 * 内部走 {@link RawSseStreamingChatModel} 自建 SSE 客户端（reasoning_content 与 content
 * 分离上抛），事件到达期间按节流（每秒至多一条）向 sink 推「思考中·已思 N 字」+ 完整
 * 思考文本（思考阶段，模型 reasoning_content 全文实时上屏）或「应答中·已收 N 字」+
 * 完整应答文本（正式应答阶段），轮询 live 行替换式展示；另在思考→应答切换点补推尾部思考、
 * onComplete 推「【LLM 思考归档】」独立行，保证每轮思考全文可被前端累积留存（替换式
 * live 不再造成上一轮思考丢失），让业务进度窗在单次调用数十秒的思考静默窗内实时看见
 * LLM 完整的思考过程与应答内容；onComplete 返回完整响应，
 * onError 抛异常（cause 链保留，引擎回落/中断识别逻辑与同步路径同口径复用）。
 * sink 为 null 时退化为纯流式转同步。
 * 精简模式（liveVerbose=false，事件流追加式链路专用）：流式期间仅推状态行（思考中·已思 N 字 /
 * 应答中·已收 N 字），思考全文只在轮末归档推一条——追加式事件流若逐条携带累积全文，
 * 事件体积随思考长度平方膨胀（Redis 存储与轮询传输双重代价）
 *
 * @author Sharp
 * @since 2026/8/21
 */
@Slf4j
public class StreamingProgressChatModel implements ChatLanguageModel {

    /** live 进度推流节流间隔（ms）：轮询间隔 2s，每秒至多一条保证每次轮询都能看到变化 */
    private static final long LIVE_INTERVAL_MS = 1000;

    /** 等待首个事件心跳周期（s）：静默窗内 live 行带秒数刷新，避免看似卡死 */
    private static final long WAIT_HEARTBEAT_SECS = 5;

    /** 闩等待宽限（s）：须大于底层 okhttp callTimeout（=timeoutSeconds），否则两者同时到期时
     *  门面先报「未收口」泛化错误，吞掉客户端真实错误且回落降级不触发（600s 未收口案例） */
    private static final long AWAIT_GRACE_SECONDS = 30;

    /** 全局守护心跳调度器（等待首个 SSE 事件期间推「已等 Ns」） */
    private static final ScheduledExecutorService HEARTBEAT = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "llm-live-heartbeat");
        t.setDaemon(true);
        return t;
    });

    private final RawSseStreamingChatModel delegate;
    private final Consumer<String> liveSink;
    private final long timeoutSeconds;
    /** 流式首个应答 token 前失败的回落（如网关不支持 stream/stream+tools）；null 表示不回落 */
    private final ChatLanguageModel fallback;
    /** live 推送形态：true=状态行+累积全文（替换式展示链路）；false=仅状态行，全文靠轮末归档（追加式事件流链路） */
    private final boolean liveVerbose;
    /** 首次回落后同实例后续调用直接走同步，避免每轮试探已知损坏的流式通道 */
    private final AtomicBoolean streamBroken = new AtomicBoolean();

    public StreamingProgressChatModel(RawSseStreamingChatModel delegate, Consumer<String> liveSink,
                                      long timeoutSeconds) {
        this(delegate, liveSink, timeoutSeconds, null);
    }

    public StreamingProgressChatModel(RawSseStreamingChatModel delegate, Consumer<String> liveSink,
                                      long timeoutSeconds, ChatLanguageModel fallback) {
        this(delegate, liveSink, timeoutSeconds, fallback, true);
    }

    public StreamingProgressChatModel(RawSseStreamingChatModel delegate, Consumer<String> liveSink,
                                      long timeoutSeconds, ChatLanguageModel fallback, boolean liveVerbose) {
        this.delegate = delegate;
        this.liveSink = liveSink;
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : 600;
        this.fallback = fallback;
        this.liveVerbose = liveVerbose;
    }

    private boolean syncOnly() {
        return fallback != null && streamBroken.get();
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        if (syncOnly()) {
            return fallback.generate(messages);
        }
        return call(messages, null, () -> fallback.generate(messages));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, List<ToolSpecification> specs) {
        if (syncOnly()) {
            return fallback.generate(messages, specs);
        }
        return call(messages, specs, () -> fallback.generate(messages, specs));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages, ToolSpecification spec) {
        if (syncOnly()) {
            return fallback.generate(messages, spec);
        }
        return call(messages, Collections.singletonList(spec), () -> fallback.generate(messages, spec));
    }

    /** 流式发起 + 阻塞收口：首个事件前按心跳推「已等 Ns」；思考阶段推思考内容预览，
     *  应答阶段推应答内容预览（均按节流）；自建客户端保证 onError 必达，无需库层缺陷桥接 */
    private Response<AiMessage> call(List<ChatMessage> messages, List<ToolSpecification> specs,
                                     Supplier<Response<AiMessage>> syncRetry) {
        long startMs = System.currentTimeMillis();
        int promptChars = 0;
        for (ChatMessage m : messages) {
            promptChars += String.valueOf(m).length();
        }
        // 与「首事件延迟」「流式完成」组成证据链：发起 t0 → 首事件 t0+N → 完成，静默窗归属模型侧
        log.info("LLM 流式调用发起：消息 {} 条·prompt 约 {} 字·tools {} 个",
                messages.size(), promptChars, specs == null ? 0 : specs.size());
        StringBuilder reasoningBuf = new StringBuilder();
        StringBuilder contentBuf = new StringBuilder();
        AtomicInteger reasoningChars = new AtomicInteger();
        AtomicInteger contentChars = new AtomicInteger();
        AtomicLong lastPush = new AtomicLong();
        AtomicBoolean firstEvent = new AtomicBoolean();
        AtomicReference<Response<AiMessage>> respRef = new AtomicReference<>();
        AtomicReference<Throwable> errRef = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        push("等待 LLM 流式响应…");
        // 等待首事件心跳：模型排队/预填充期间 live 行带秒数刷新；首个事件（思考或应答）到达后自停
        ScheduledFuture<?> heartbeat = HEARTBEAT.scheduleAtFixedRate(() -> {
            if (!firstEvent.get()) {
                push("等待 LLM 流式响应… 已等 " + (System.currentTimeMillis() - startMs) / 1000 + "s");
            }
        }, WAIT_HEARTBEAT_SECS, WAIT_HEARTBEAT_SECS, TimeUnit.SECONDS);
        delegate.generate(messages, specs, new RawSseStreamingChatModel.Listener() {
            @Override
            public void onReasoning(String delta) {
                if (firstEvent.compareAndSet(false, true)) {
                    log.info("LLM 流式首事件（思考）延迟 {}s", (System.currentTimeMillis() - startMs) / 1000);
                }
                reasoningBuf.append(delta);
                int n = reasoningChars.addAndGet(delta.length());
                // 思考全文实时上屏：状态行 + 完整累积文本（不截断），节流推送（仅 verbose 模式；
                // 精简模式只推状态行，全文由轮末归档统一给出）；
                // 最后一次节流后的尾部思考由应答首 token 前的收尾推送补全，归档不丢字
                long now = System.currentTimeMillis();
                if (now - lastPush.get() >= LIVE_INTERVAL_MS) {
                    lastPush.set(now);
                    push(liveVerbose
                            ? "思考中·已思 " + n + " 字\n" + reasoningBuf
                            : "思考中·已思 " + n + " 字…");
                }
            }

            @Override
            public void onToken(String delta) {
                if (firstEvent.compareAndSet(false, true)) {
                    // 首应答 token 延迟即 time-to-first-token：定位「慢在模型思考/排队」还是「网关缓冲」
                    log.info("LLM 流式首应答 token 延迟 {}s", (System.currentTimeMillis() - startMs) / 1000);
                }
                if (contentBuf.length() == 0 && liveVerbose) {
                    // 思考→应答切换点：补推最后一次节流后的尾部思考全文，前端归档不丢尾部片段；
                    // 纯应答调用（无思考）天然跳过。归档（含最终全文）统一由 onComplete 兜底推送，
                    // live 为替换式展示——不补此处则上一轮思考在下一轮开始时被覆盖丢失（debug 无法回看）；
                    // 精简模式为追加式事件流无覆盖丢失问题，跳过此处由归档兜底
                    push("思考中·已思 " + reasoningChars.get() + " 字\n" + reasoningBuf);
                }
                contentBuf.append(delta);
                int n = contentChars.addAndGet(delta.length());
                long now = System.currentTimeMillis();
                // 首 token 立即推一次（思考→应答切换点用户即刻可见），之后按节流间隔推；
                // verbose 携带累积全文（替换式展示），精简模式仅状态行防追加式事件流膨胀
                if (n == delta.length() || now - lastPush.get() >= LIVE_INTERVAL_MS) {
                    lastPush.set(now);
                    push(liveVerbose
                            ? "应答中·已收 " + n + " 字\n" + contentBuf
                            : "应答中·已收 " + n + " 字…");
                }
            }

            @Override
            public void onComplete(Response<AiMessage> response) {
                log.info("LLM 流式完成：总 {}s·思考 {} 字·应答 {} 字",
                        (System.currentTimeMillis() - startMs) / 1000, reasoningChars.get(), contentChars.get());
                // 本次调用思考全文归档：以独立过程日志行经 live 通道推给前端（与 steps 同源，
                // 完成态折叠区可回看 + 一键复制可带走），杜绝多轮覆盖丢失——思考过程是 debug 证据链的一部分；
                // 无思考的纯应答调用不推（避免空归档噪音）
                if (reasoningChars.get() > 0) {
                    push("【LLM 思考归档】" + reasoningChars.get() + " 字\n" + reasoningBuf);
                }
                respRef.set(response);
                done.countDown();
            }

            @Override
            public void onError(Throwable error) {
                log.warn("LLM 流式失败（总 {}s）：{}", (System.currentTimeMillis() - startMs) / 1000,
                        error == null ? "" : error.getMessage());
                errRef.compareAndSet(null, error);
                done.countDown();
            }
        });
        boolean completed;
        try {
            completed = done.await(timeoutSeconds + AWAIT_GRACE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM 流式生成被中断", e);
        } finally {
            heartbeat.cancel(false);
        }
        if (!completed) {
            // 附证据链：首事件是否到达 + 已收字数，区分「网关无响应」与「流中途丢失」
            log.warn("LLM 流式调用 {}s 未收口（无 onComplete/onError）：首事件{}，思考 {} 字，应答 {} 字",
                    timeoutSeconds, firstEvent.get() ? "已到" : "未到", reasoningChars.get(), contentChars.get());
            throw new RuntimeException("LLM 流式响应 " + timeoutSeconds + "s 未收口（首事件"
                    + (firstEvent.get() ? "已到" : "未到") + "），请检查 LLM 网关/模型服务状态");
        }
        Response<AiMessage> resp = respRef.get();
        if (resp != null && resp.content() != null) {
            return resp;
        }
        Throwable err = errRef.get();
        // 未收到任何应答内容且配了回落：降级同步重试（典型场景：网关不支持 stream 或 stream+tools）；
        // 思考内容已流出则不回落（流式通道本身健康，避免重复消耗一次整调用）
        if (err != null && contentChars.get() == 0 && fallback != null) {
            streamBroken.set(true);
            log.warn("LLM 流式失败（未产出应答内容），降级同步模型重试：{}", err.getMessage());
            push("流式通道不可用，降级同步模型…");
            return syncRetry.get();
        }
        if (err != null) {
            throw new RuntimeException("LLM 流式生成失败: " + err.getMessage(), err);
        }
        // onComplete 未带响应体时以累积片段兜底
        return Response.from(AiMessage.from(contentBuf.toString()));
    }

    private void push(String text) {
        if (liveSink != null) {
            liveSink.accept(text);
        }
    }
}
