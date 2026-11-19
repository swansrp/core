package com.bidr.llm.agent.health;

import com.bidr.document.spi.AgentHealthContributor;
import com.bidr.llm.agent.file.AgentCapabilityHealthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Title: AgentCapabilityHealthProbe
 * Description: agent 依赖底座的<b>能力探活聚合器</b>（2026-11-18 探活架构化重写——用户拍板
 * 「每个子模块自己探自己的，中央只收集；没有这个子模块就不收集」）：探测细节全部下沉到各腿的
 * {@link AgentHealthContributor} 实现（mineru/gotenberg/cube/openhands 各自内聚——地址/路径/
 * 鉴权/代理/可达判定与真实客户端同模块同源，消灭 11-14 版「中央手抄客户端调用方式」的双轨漂移；
 * 那版恒探 OpenHands 而 11-16 才有 Cube 腿 ⇒ 6.60 误报 exec.sandbox ✕ 即漂移实证），本类只做
 * 四件事：
 * <ul>
 *     <li><b>聚合</b>：按注入的 contributor 列表（Spring {@code @Order} 稳序）逐个 {@code probe()}
 *         （单腿异常兜底转 UNREACHABLE 不影响其它腿），聚合成健康快照＋一行摘要；</li>
 *     <li><b>调度</b>：启动一次＋daemon 定时刷新（周期 {@code llm.agent.health-probe.interval-seconds}
 *         缺省 300，≤0 关定时只留启动一次）；</li>
 *     <li><b>日志</b>：只在状态变化时打一行（{@code Agent 能力探活: ocr(mineru)=…, …}；含
 *         「不可达」用 WARN＝降级，其余 INFO＝恢复/首行），不刷屏；</li>
 *     <li><b>上报</b>：运行级健康快照（{@link AgentCapabilityHealthProvider} 端口实现，经
 *         {@code /agents} 契约给前端徽标）＋状态变化即 SSE 广播（{@link AgentHealthSseHub}，
 *         hub 缺席静默跳过）。</li>
 * </ul>
 * <b>不改能力位</b>：{@code /agents} 契约的 capabilities 仍按声明级推导，探活失败只进健康快照
 * 与日志（徽标变灰），能力位与相关工具面不受影响；<b>不阻断启动</b>：探针异常一律吞掉
 * （ApplicationRunner 内全兜底）。快照键＝contributor 的 {@code capability()}——模块不在场＝
 * bean 不存在＝该键自然缺席（前端缺键＝按可用处理，历史兼容），「没这个子模块就不收集」。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class AgentCapabilityHealthProbe implements ApplicationRunner, AutoCloseable, AgentCapabilityHealthProvider {

    private final List<AgentHealthContributor> contributors;

    private final long intervalSeconds;

    /** 健康度 SSE 广播口（可 null＝未装配静默跳过）：状态变化时把最新快照推全员 */
    private final AgentHealthSseHub healthHub;

    /** 上次探测的一行摘要（null＝尚未探测过）；与本次相同＝状态未变不打日志 */
    private volatile String lastLine;

    /** 运行级健康快照（探测前初始化为空＝收集面未知；每轮探测后整体替换） */
    private volatile Map<String, String> lastSnapshot = Collections.emptyMap();

    private volatile ScheduledExecutorService scheduler;

    public AgentCapabilityHealthProbe(List<AgentHealthContributor> contributors, long intervalSeconds,
                                      AgentHealthSseHub healthHub) {
        this.contributors = contributors == null ? List.of() : sortContributors(contributors);
        this.intervalSeconds = intervalSeconds;
        this.healthHub = healthHub;
    }

    /** 按 contributor 自报 order 稳序（Spring 注入 List 已按 @Order 排过，此处兜底 order() 口径） */
    private static List<AgentHealthContributor> sortContributors(List<AgentHealthContributor> contributors) {
        List<AgentHealthContributor> sorted = new ArrayList<>(contributors);
        sorted.sort(java.util.Comparator.comparingInt(AgentHealthContributor::order));
        return Collections.unmodifiableList(sorted);
    }

    /** 启动一次＋（interval-seconds&gt;0 时）daemon 定时刷新；任何异常吞掉不阻断启动 */
    @Override
    public void run(ApplicationArguments args) {
        safeProbe("启动探测");
        if (intervalSeconds > 0) {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "agent-capability-health-probe");
                thread.setDaemon(true);
                return thread;
            });
            scheduler = executor;
            executor.scheduleWithFixedDelay(this::safeProbeTick, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        }
    }

    /** 探测一轮：算出一行摘要，与上次相同不打（返回 false＝状态未变）；变化打一行（含「不可达」WARN） */
    boolean probeOnce() {
        List<String> segments = new ArrayList<>();
        Map<String, String> snapshot = new LinkedHashMap<>();
        for (AgentHealthContributor contributor : contributors) {
            AgentHealthContributor.HealthReport report;
            try {
                report = contributor.probe();
            } catch (Exception e) {
                // 单腿异常兜底：探针实现坏了只降自己那一位，不拖累其它腿与聚合轮
                report = AgentHealthContributor.HealthReport.unreachable(
                        "探针异常(" + e.getClass().getSimpleName() + ")");
            }
            segments.add(capabilitySegment(contributor, report));
            snapshot.merge(capabilityKey(contributor), healthValue(report), (oldValue, newValue) -> newValue);
        }
        lastSnapshot = Collections.unmodifiableMap(snapshot);
        String line = "Agent 能力探活: " + String.join(", ", segments);
        String previous = lastLine;
        lastLine = line;
        if (line.equals(previous)) {
            return false;
        }
        if (line.contains("不可达")) {
            log.warn("{}", line);
        } else {
            log.info("{}", line);
        }
        // 状态变化即广播最新快照（SSE health 事件，前端徽标实时翻转）；hub 缺席＝未装配
        // 静默跳过，SSE 任何异常不影响探活本体
        if (healthHub != null) {
            try {
                healthHub.broadcast("health", lastSnapshot);
            } catch (Exception e) {
                log.debug("Agent 探活快照 SSE 广播失败（不影响探活）: {}", String.valueOf(e.getMessage()));
            }
        }
        return true;
    }

    /** 日志行段：capability(probeName)=状态（如 ocr(mineru)=可达） */
    private static String capabilitySegment(AgentHealthContributor contributor,
                                            AgentHealthContributor.HealthReport report) {
        return capabilityKey(contributor) + "(" + contributor.probeName() + ")=" + text(report);
    }

    /** 快照键：capability() 空白时按 probeName 兜底（实现错误防御，不炸聚合轮） */
    private static String capabilityKey(AgentHealthContributor contributor) {
        String capability = contributor.capability();
        return StringUtils.hasText(capability) ? capability.trim() : contributor.probeName();
    }

    private static String text(AgentHealthContributor.HealthReport report) {
        switch (report.getStatus()) {
            case REACHABLE:
                return "可达";
            case NOT_CONFIGURED:
                return "未配置";
            default:
                return "不可达(" + (StringUtils.hasText(report.getReason()) ? report.getReason() : "原因未知") + ")";
        }
    }

    private static String healthValue(AgentHealthContributor.HealthReport report) {
        switch (report.getStatus()) {
            case REACHABLE:
                return AgentCapabilityHealthProvider.UP;
            case NOT_CONFIGURED:
                return AgentCapabilityHealthProvider.UNCONFIGURED;
            default:
                return AgentCapabilityHealthProvider.DOWN;
        }
    }

    private void safeProbe(String phase) {
        try {
            probeOnce();
        } catch (Exception e) {
            log.warn("Agent 能力探活{}异常（不阻断启动，不改能力位）: {}", phase, String.valueOf(e.getMessage()));
        }
    }

    private void safeProbeTick() {
        try {
            probeOnce();
        } catch (Exception e) {
            // 定时轮次异常吞掉：探活绝不反向影响宿主
        }
    }

    /** 健康快照（端口实现）：不可变副本，读侧共享安全；键集＝在场 contributor 的能力位 */
    @Override
    public Map<String, String> snapshot() {
        return Map.copyOf(lastSnapshot);
    }

    /** 上次探测的一行摘要（测试口；null＝尚未探测） */
    String lastLoggedLine() {
        return lastLine;
    }

    /** 定时器在场与否（测试口；interval-seconds≤0 恒 null） */
    ScheduledExecutorService scheduler() {
        return scheduler;
    }

    /** 健康度 SSE 广播口（public 测试口：装配测试在 capability 包，跨包断言探活件↔hub 接线同源） */
    public AgentHealthSseHub healthHub() {
        return healthHub;
    }

    @Override
    public void close() {
        ScheduledExecutorService executor = scheduler;
        if (executor != null) {
            executor.shutdownNow();
            scheduler = null;
        }
    }
}
