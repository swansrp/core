package com.bidr.agent.capability.health;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Title: AgentHealthSseHubTest
 * Description: 健康度 SSE 广播注册表契约钉桩（{@link SseEmitter} 录制子件，不打真 HTTP——
 * 子类重写 {@code send(SseEventBuilder)} 截取 builder 帧，重写 onX 捕获 hub 注册的清理回调）：
 * <ul>
 *     <li>register：立即发 connected 帧（event:connected＋data:ok），完成/异常回调捕获，
 *         触发即摘除（后续广播不再投递）；首帧发送失败＝摘除不占位；</li>
 *     <li>broadcast：全员逐连接投递（事件名 health＋载荷对象原样透传——JSON 序列化在 MVC
 *         层完成，非 hub 职责）；发送失败的连接主动收尾（complete）＋摘除，不影响其余订阅者；</li>
 *     <li>心跳 pingAll：comment {@code :ping} 注释帧全员保活，失败连接同样摘除；</li>
 *     <li>shutdown：心跳调度器停摆（isShutdown）＋全部在挂连接收尾。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/14
 */
class AgentHealthSseHubTest {

    private AgentHealthSseHub hub;

    @AfterEach
    void shutdownHub() {
        if (hub != null) {
            hub.shutdown();
        }
    }

    /** 录制子件：send 截取 builder 帧不落真流；onX 回调捕获供测试触发；可注入 send 失败模拟断连 */
    private static final class RecordingEmitter extends SseEmitter {

        final List<ResponseBodyEmitter.DataWithMediaType> frame = new ArrayList<>();
        final List<Runnable> completionCallbacks = new ArrayList<>();
        final List<Consumer<Throwable>> errorCallbacks = new ArrayList<>();
        boolean completed = false;
        /** 可变开关：先注册成功再置位＝模拟「存活中断」（register 首帧失败路径由 register 用例覆盖） */
        boolean failOnSend;

        RecordingEmitter(boolean failOnSend) {
            super(0L);
            this.failOnSend = failOnSend;
        }

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            if (failOnSend) {
                throw new IOException("connection broken");
            }
            frame.addAll(builder.build());
        }

        @Override
        public synchronized void onCompletion(Runnable callback) {
            completionCallbacks.add(callback);
            super.onCompletion(callback);
        }

        @Override
        public synchronized void onTimeout(Runnable callback) {
            // 超时与完成同语义（同进完成回调清单，摘除断言共用）
            completionCallbacks.add(callback);
            super.onTimeout(callback);
        }

        @Override
        public synchronized void onError(Consumer<Throwable> callback) {
            errorCallbacks.add(callback);
            super.onError(callback);
        }

        @Override
        public synchronized void complete() {
            completed = true;
            super.complete();
        }
    }

    /** 录制件收到的文本帧拼回一段（SSE 帧文本可能按 builder 内部缓冲拆块） */
    private static String frameText(RecordingEmitter emitter) {
        StringBuilder text = new StringBuilder();
        for (ResponseBodyEmitter.DataWithMediaType entry : emitter.frame) {
            if (entry.getData() instanceof String) {
                text.append((String) entry.getData());
            }
        }
        return text.toString();
    }

    @Test
    @DisplayName("register：立即发 connected 帧（event:connected＋data:ok）；完成回调触发即摘除")
    void registerSendsConnectedFrameAndCleansOnCompletion() {
        hub = new AgentHealthSseHub();
        RecordingEmitter subscriber = new RecordingEmitter(false);
        hub.register(subscriber);

        assertThat(frameText(subscriber))
                .as("建连确认帧")
                .contains("event:connected")
                .contains("data:ok");

        subscriber.completionCallbacks.forEach(Runnable::run);
        hub.broadcast("health", "after-cleanup");
        assertThat(frameText(subscriber))
                .as("完成回调触发后摘除：后续广播不再投递")
                .doesNotContain("after-cleanup");
    }

    @Test
    @DisplayName("register：异常钩子触发即摘除；首帧发送失败＝摘除不占位")
    void registerCleansOnErrorAndSurvivesFirstSendFailure() {
        hub = new AgentHealthSseHub();
        RecordingEmitter subscriber = new RecordingEmitter(false);
        hub.register(subscriber);
        subscriber.errorCallbacks.forEach(callback -> callback.accept(new RuntimeException("boom")));
        hub.broadcast("health", "after-error");
        assertThat(frameText(subscriber))
                .as("异常钩子触发后摘除")
                .doesNotContain("after-error");

        RecordingEmitter broken = new RecordingEmitter(true);
        hub.register(broken);
        hub.broadcast("health", "nobody-gets-this");
        assertThat(broken.frame).as("首帧发送失败＝已摘除，广播不再到达").isEmpty();
    }

    @Test
    @DisplayName("broadcast：全员投递（事件名 health，载荷对象原样透传）；失败连接收尾＋摘除不影响其余")
    void broadcastDeliversToAllAndDropsBrokenConnections() {
        hub = new AgentHealthSseHub();
        RecordingEmitter good = new RecordingEmitter(false);
        RecordingEmitter broken = new RecordingEmitter(false);
        hub.register(good);
        hub.register(broken);
        int brokenFrameAfterRegister = broken.frame.size();

        Map<String, String> snapshot = new LinkedHashMap<>();
        snapshot.put("ocr", "up");
        // 存活中断：注册成功后置失败，广播时才 send 抛
        broken.failOnSend = true;
        hub.broadcast("health", snapshot);

        assertThat(frameText(good))
                .as("事件名随帧下发")
                .contains("event:health");
        assertThat(good.frame)
                .extracting(ResponseBodyEmitter.DataWithMediaType::getData)
                .as("载荷对象透传（JSON 序列化在 MVC 层）")
                .contains(snapshot);
        assertThat(broken.completed)
                .as("发送失败的连接被主动收尾")
                .isTrue();
        assertThat(broken.frame.size())
                .as("本轮投递失败，帧未落")
                .isEqualTo(brokenFrameAfterRegister);

        hub.broadcast("health", "second-frame");
        assertThat(broken.frame.size())
                .as("失败连接已摘除，不再投递")
                .isEqualTo(brokenFrameAfterRegister);
        assertThat(frameText(good))
                .as("其余订阅者不受影响")
                .contains("second-frame");
    }

    @Test
    @DisplayName("心跳 pingAll：comment :ping 全员保活；失败连接摘除")
    void pingAllSendsCommentFrameAndDropsBrokenConnections() {
        hub = new AgentHealthSseHub();
        RecordingEmitter good = new RecordingEmitter(false);
        RecordingEmitter broken = new RecordingEmitter(false);
        hub.register(good);
        hub.register(broken);
        int brokenFrameAfterRegister = broken.frame.size();
        broken.failOnSend = true;

        hub.pingAll();
        assertThat(frameText(good))
                .as("SSE 注释帧保活（不触发前端事件）")
                .contains(":ping");
        assertThat(broken.completed).as("心跳发送失败＝摘除并收尾").isTrue();
        assertThat(broken.frame.size())
                .as("心跳投递失败，帧未落")
                .isEqualTo(brokenFrameAfterRegister);

        hub.pingAll();
        long pingCount = frameText(good).split(":ping", -1).length - 1;
        assertThat(pingCount).as("失败连接摘除后不再重复投递；存活连接继续保活").isEqualTo(2);
        assertThat(broken.frame.size())
                .as("摘除后心跳不再到达")
                .isEqualTo(brokenFrameAfterRegister);
    }

    @Test
    @DisplayName("shutdown：心跳调度器停摆＋全部在挂连接收尾")
    void shutdownStopsHeartbeatAndCompletesSubscribers() {
        hub = new AgentHealthSseHub();
        RecordingEmitter subscriber = new RecordingEmitter(false);
        hub.register(subscriber);

        hub.shutdown();
        assertThat(hub.heartbeat().isShutdown())
                .as("心跳调度器已停摆")
                .isTrue();
        assertThat(subscriber.completed).as("在挂连接被收尾").isTrue();
        hub = null;
    }
}
