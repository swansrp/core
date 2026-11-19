package com.bidr.llm.agent.health;

import com.bidr.document.spi.AgentHealthContributor;
import com.bidr.llm.agent.file.AgentCapabilityHealthProvider;
import com.bidr.llm.agent.file.AgentFileCapabilityResolver;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Title: AgentCapabilityHealthProbeTest
 * Description: 探活聚合器契约钉桩（contributor 打桩，不打外网）：
 * <ul>
 *     <li>聚合：按 contributor 自报 order 稳序逐个探，行＝capability(probeName)=状态；
 *         快照键=capability、值小写三态；</li>
 *     <li>缺席即缺项：contributor 不在场＝该能力位不进行与快照（「没这个子模块就不收集」）；</li>
 *     <li>兜底：单腿 probe() 抛异常＝该位 UNREACHABLE(探针异常)，其余腿不受影响；</li>
 *     <li>状态变化：首轮必出一行，同状态不出（probeOnce 返回 false），变化再出一行；
 *         含「不可达」用 WARN 级（行内容判定）；</li>
 *     <li>SSE 广播：状态变化即 broadcast("health", 最新快照)；同状态不广播；</li>
 *     <li>触发面：interval-seconds≤0 不建定时器只留启动一次；&gt;0 建 daemon 定时器，close() 即关。</li>
 * </ul>
 * 各腿探测语义（HTTP 状态码判定/鉴权头/代理）已下沉到各腿模块自己的测试——这里只钉聚合行为。
 * P3-0 随探活三件套自 agent-capability 迁入 llm；本模块测试约定＝JUnit 4（kernel 传递 TestNG
 * provider 执行），故由 jupiter 原样转换（断言仍 assertj、打桩仍 mockito，@DisplayName 落行注）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
public class AgentCapabilityHealthProbeTest {

    /** 固定结果打桩腿：capability/probeName/order 可定制，probe 返回预置 HealthReport */
    private static final class StubLeg implements AgentHealthContributor {
        private final String capability;
        private final String probeName;
        private final int order;
        private AgentHealthContributor.HealthReport report =
                AgentHealthContributor.HealthReport.reachable();
        private boolean throwOnProbe = false;
        private int calls;

        StubLeg(String capability, String probeName, int order) {
            this.capability = capability;
            this.probeName = probeName;
            this.order = order;
        }

        @Override
        public String capability() {
            return capability;
        }

        @Override
        public String probeName() {
            return probeName;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public AgentHealthContributor.HealthReport probe() {
            calls++;
            if (throwOnProbe) {
                throw new IllegalStateException("腿内部炸了");
            }
            return report;
        }
    }

    /** 聚合与排序：按 order 稳序逐个探，行=capability(probeName)=状态，快照键=capability */
    @Test
    public void aggregatesInOrderAndBuildsSnapshot() {
        StubLeg ocr = new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100);
        StubLeg sandbox = new StubLeg(AgentFileCapabilityResolver.EXEC_SANDBOX, "cube", 300);
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(
                Arrays.asList(sandbox, ocr), 0, null);
        // 故意倒序传入（sandbox 在前）——聚合应按 order 恢复 mineru→sandbox 稳序
        assertThat(probe.probeOnce()).as("首轮必出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, exec.sandbox(cube)=可达");
        assertThat(ocr.calls).as("两腿各探一次").isEqualTo(1);
        assertThat(sandbox.calls).isEqualTo(1);
        assertThat(probe.snapshot())
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UP)
                .containsEntry(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.UP);
    }

    /** 缺席即缺项：contributor 不在场＝该能力位不进行与快照（没这个子模块就不收集） */
    @Test
    public void absentLegIsAbsentFromLineAndSnapshot() {
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(
                Collections.singletonList(new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100)),
                0, null);
        probe.probeOnce();
        assertThat(probe.lastLoggedLine())
                .doesNotContain("exec.sandbox")
                .doesNotContain("artifacts.doc");
        assertThat(probe.snapshot()).containsOnlyKeys(AgentFileCapabilityResolver.OCR);
    }

    /** 三态映射：REACHABLE=up / NOT_CONFIGURED=unconfigured / UNREACHABLE=down(原因入行) */
    @Test
    public void mapsThreeStatesToHealthValues() {
        StubLeg ocr = new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100);
        StubLeg artifacts = new StubLeg(AgentFileCapabilityResolver.ARTIFACTS_DOC, "gotenberg", 200);
        StubLeg sandbox = new StubLeg(AgentFileCapabilityResolver.EXEC_SANDBOX, "cube", 300);
        artifacts.report = AgentHealthContributor.HealthReport.notConfigured();
        sandbox.report = AgentHealthContributor.HealthReport.unreachable("HTTP 503");
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(Arrays.asList(ocr, artifacts, sandbox), 0, null);
        probe.probeOnce();
        assertThat(probe.snapshot())
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UP)
                .containsEntry(AgentFileCapabilityResolver.ARTIFACTS_DOC, AgentCapabilityHealthProvider.UNCONFIGURED)
                .containsEntry(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.DOWN);
        assertThat(probe.lastLoggedLine())
                .isEqualTo("Agent 能力探活: ocr(mineru)=可达, artifacts.doc(gotenberg)=未配置,"
                        + " exec.sandbox(cube)=不可达(HTTP 503)");
    }

    /** 兜底：单腿 probe() 抛异常＝该位不可达(探针异常)，其余腿不受影响 */
    @Test
    public void singleLegFailureIsolated() {
        StubLeg ocr = new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100);
        StubLeg sandbox = new StubLeg(AgentFileCapabilityResolver.EXEC_SANDBOX, "cube", 300);
        sandbox.throwOnProbe = true;
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(Arrays.asList(ocr, sandbox), 0, null);
        assertThat(probe.probeOnce()).isTrue();
        assertThat(ocr.calls).as("其它腿照常探").isEqualTo(1);
        assertThat(probe.snapshot())
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UP)
                .containsEntry(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.DOWN);
        assertThat(probe.lastLoggedLine()).contains("exec.sandbox(cube)=不可达(探针异常(IllegalStateException))");
    }

    /** 状态变化：首轮出一行，同状态不出，变化再出一行；不可达行走 WARN（内容判定） */
    @Test
    public void logsOnlyOnStateChange() {
        StubLeg ocr = new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100);
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(
                Collections.singletonList(ocr), 0, null);
        assertThat(probe.probeOnce()).as("首轮必出一行").isTrue();
        assertThat(probe.probeOnce()).as("同状态不出").isFalse();
        ocr.report = AgentHealthContributor.HealthReport.unreachable("ConnectException");
        assertThat(probe.probeOnce()).as("降级出一行").isTrue();
        assertThat(probe.lastLoggedLine())
                .contains("ocr(mineru)=不可达(ConnectException)")
                .as("含「不可达」＝降级行");
        assertThat(probe.probeOnce()).as("同状态仍不出").isFalse();
    }

    /** SSE 广播：状态变化即 broadcast("health", 最新快照)；同状态不广播 */
    @Test
    @SuppressWarnings("unchecked")
    public void stateChangeBroadcastsSnapshotToHub() {
        AgentHealthSseHub hub = mock(AgentHealthSseHub.class);
        StubLeg ocr = new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100);
        StubLeg sandbox = new StubLeg(AgentFileCapabilityResolver.EXEC_SANDBOX, "cube", 300);
        sandbox.report = AgentHealthContributor.HealthReport.notConfigured();
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(Arrays.asList(ocr, sandbox), 0, hub);

        assertThat(probe.probeOnce()).as("首轮状态变化").isTrue();
        ArgumentCaptor<Map<String, String>> first = ArgumentCaptor.forClass(Map.class);
        verify(hub, times(1)).broadcast(eq("health"), first.capture());
        assertThat(first.getValue())
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.UP)
                .containsEntry(AgentFileCapabilityResolver.EXEC_SANDBOX, AgentCapabilityHealthProvider.UNCONFIGURED);

        probe.probeOnce();
        verify(hub, times(1)).broadcast(anyString(), any());

        ocr.report = AgentHealthContributor.HealthReport.unreachable("HTTP 503");
        assertThat(probe.probeOnce()).as("降级即变化").isTrue();
        ArgumentCaptor<Map<String, String>> second = ArgumentCaptor.forClass(Map.class);
        verify(hub, times(2)).broadcast(eq("health"), second.capture());
        assertThat(second.getValue())
                .containsEntry(AgentFileCapabilityResolver.OCR, AgentCapabilityHealthProvider.DOWN);
    }

    /** interval-seconds≤0：只留启动探测不建定时器；>0 建 daemon 定时器，close() 即关 */
    @Test
    public void schedulerFollowsIntervalConfig() {
        AgentCapabilityHealthProbe noSchedule = new AgentCapabilityHealthProbe(
                Collections.<AgentHealthContributor>emptyList(), 0, null);
        noSchedule.run(null);
        assertThat(noSchedule.scheduler()).isNull();

        AgentCapabilityHealthProbe scheduled = new AgentCapabilityHealthProbe(
                Collections.<AgentHealthContributor>emptyList(), 1, null);
        scheduled.run(null);
        assertThat(scheduled.scheduler()).isNotNull();
        assertThat(scheduled.scheduler().isShutdown()).isFalse();
        scheduled.close();
        assertThat(scheduled.scheduler()).isNull();
    }

    /** 快照：探测前为空集（收集面未知）；探测后刷新；snapshot() 不可变副本；capability 空白＝probeName 兜底键 */
    @Test
    public void snapshotLifecycleAndImmutability() {
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(
                Collections.<AgentHealthContributor>emptyList(), 0, null);
        assertThat(probe.snapshot()).isEmpty();
        assertThatThrownBy(() -> probe.snapshot().putIfAbsent("x", "up"))
                .isInstanceOf(UnsupportedOperationException.class);

        StubLeg weird = new StubLeg("  ", "weird-leg", 400);
        weird.report = AgentHealthContributor.HealthReport.unreachable("boom");
        AgentCapabilityHealthProbe probed = new AgentCapabilityHealthProbe(
                Collections.<AgentHealthContributor>singletonList(weird), 0, null);
        probed.probeOnce();
        assertThat(probed.snapshot()).as("capability 空白＝probeName 兜底（实现错误不炸聚合轮）")
                .containsEntry("weird-leg", AgentCapabilityHealthProvider.DOWN);
        assertThat(probed.lastLoggedLine()).contains("weird-leg(weird-leg)=不可达(boom)");
    }

    /** 启动接线：run(null) 即刻探一轮（ApplicationRunner 语义） */
    @Test
    public void runProbesOnceImmediately() {
        StubLeg ocr = new StubLeg(AgentFileCapabilityResolver.OCR, "mineru", 100);
        AgentCapabilityHealthProbe probe = new AgentCapabilityHealthProbe(
                Collections.<AgentHealthContributor>singletonList(ocr), 0, null);
        probe.run(null);
        assertThat(ocr.calls).isEqualTo(1);
        assertThat(probe.lastLoggedLine()).isNotNull();
        assertThat(new ArrayList<>(probe.snapshot().keySet())).containsExactly(AgentFileCapabilityResolver.OCR);
    }
}
