package com.bidr.agent.console.usage;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.agent.console.dao.repository.ModelUsageService;
import com.bidr.llm.usage.ModelUsageFacts;
import com.bidr.llm.usage.ModelUsageSink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Title: ModelUsageRecorder
 * Description: 模型用量落库腿（M14 棒5）：接 {@code core/llm} 模型包装层发出的用量事实，异步落
 * {@code sys_model_usage}，一次模型调用一行。
 *
 * <p>🔴 <b>绝不影响主流程</b>：本类跑在模型调用返回之后的那一步，任何一步出问题都只记日志——
 * 队列满就丢、落库异常就吞。用量表是<b>运营读数</b>，不是业务链路的一部分，
 * 它写不进去最多是统计少一行，绝不能让会话跟着挂。</p>
 *
 * <p><b>为什么异步</b>：同步落表会把一次模型调用的收口时间拉长一条 INSERT 的 RTT，
 * 而模型调用本来就是秒级、且常在流式收口线程（okhttp 回调线程）上触发上报。
 * 这里用有界队列 + 单个守护线程：有界是为了<b>宁可丢数也不涨内存</b>（库不通时同步队列会无限堆积），
 * 单线程是为了让插入顺序与 call_at 一致，便于对账。</p>
 *
 * <p><b>丢数可见</b>：队列满时递增 {@link #droppedCount()}，并按固定间隔打一条 warn，
 * 免得静默少计。读统计的人要能看到"这段时间丢过"，否则总量会被当成真值。</p>
 *
 * <p>开关 {@code agent-console.usage.enabled}（<b>默认关</b>）：关掉后本 Bean 不存在，
 * 框架侧 {@code BeanUtil.getBean} 拿不到 ⇒ 连上报都不发，模型调用行为回到改前。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.usage", name = "enabled", havingValue = "true")
public class ModelUsageRecorder implements ModelUsageSink {

    /**
     * 队列容量：一次调用一行，2048 行＝库整整堵了约半小时的调用量才可能开始丢
     *（普通会话一轮 3–8 次调用）。再大只是把"丢"推迟，并把内存敞口放大
     */
    static final int QUEUE_CAPACITY = 2048;

    /** 单批最多落库行数（一次 poll 攒一批，减少 INSERT 往返） */
    private static final int BATCH_SIZE = 100;

    /** 拉取阻塞上限：没有新事实时线程在此 park，停止标志靠轮询检查 */
    private static final long POLL_TIMEOUT_MS = 500;

    /** 丢数 warn 间隔（每丢这么多条提醒一次，避免刷屏） */
    private static final long DROP_WARN_STEP = 100;

    private final ModelUsageService repo;

    private final ArrayBlockingQueue<ModelUsageFacts> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    private final AtomicLong dropped = new AtomicLong();

    private volatile boolean running = true;

    private Thread worker;

    @PostConstruct
    public void start() {
        worker = new Thread(this::drainLoop, "model-usage-recorder");
        worker.setDaemon(true);
        worker.start();
        log.info("模型用量落库已启动（异步队列容量={}）", QUEUE_CAPACITY);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    /**
     * 入队即返回：{@code offer} 非阻塞，队列满就计数并丢弃这一行。
     * 整段兜异常——本方法由模型调用线程直接调用，任何抛出都会污染调用方。
     */
    @Override
    public void report(ModelUsageFacts facts) {
        if (facts == null) {
            return;
        }
        try {
            if (!queue.offer(facts)) {
                long total = dropped.incrementAndGet();
                if (total % DROP_WARN_STEP == 1) {
                    log.warn("模型用量落库队列已满，本条丢弃（累计丢弃 {} 条）：库连接是否正常？", total);
                }
            }
        } catch (Exception e) {
            log.warn("模型用量入队失败（不影响模型调用）: error={}", e.getMessage());
        }
    }

    /** 累计丢弃行数（读统计时可用来判断"这一段的总量是不是下限"） */
    public long droppedCount() {
        return dropped.get();
    }

    /** 落库线程主体：攒一批→插入→清；异常整段吞，线程不因一次失败退出 */
    private void drainLoop() {
        List<ModelUsageFacts> batch = new ArrayList<>(BATCH_SIZE);
        while (running) {
            try {
                ModelUsageFacts first = queue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.clear();
                batch.add(first);
                queue.drainTo(batch, BATCH_SIZE - 1);
                insertBatch(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("模型用量落库线程异常（已继续运行）: error={}", e.getMessage());
            }
        }
        // 退出前把在途的排干：停机时丢掉的是最后这几行，值不大但没必要留
        // （batch 里可能还留着上一轮已插过的引用，先清再排，否则会把那批再插一遍）
        batch.clear();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            insertBatch(batch);
        }
    }

    private void insertBatch(List<ModelUsageFacts> factsList) {
        for (ModelUsageFacts facts : factsList) {
            try {
                repo.insert(toRow(facts));
            } catch (Exception e) {
                log.warn("模型用量落库失败（不影响模型调用）: via={} purpose={} session={} error={}",
                        facts.getVia(), facts.getPurposeType(), facts.getSessionId(), e.getMessage());
            }
        }
    }

    /**
     * 用量事实 → 落库行（无副作用，便于单测钉住形态）。
     *
     * <p>🔴 这里<b>没有任何 key 字段可写</b>：facts 携带的是 sha256 前缀指纹，
     * 明文 key 在模型层就已折成指纹，不落库、不落日志。</p>
     *
     * <p>token 与工具只数按原值传 NULL——"网关没回"与"真的 0 个"是两回事，
     * 在这里补 0 会让按人／按空间的汇总系统性少计。</p>
     */
    static ModelUsage toRow(ModelUsageFacts facts) {
        ModelUsage row = new ModelUsage();
        row.setCallKind(facts.getVia());
        row.setPurposeType(trimToNull(facts.getPurposeType()));
        row.setModelName(trimToNull(facts.getModelName()));
        row.setKeyFp(trimToNull(facts.getKeyFingerprint()));
        row.setSessionId(trimToNull(facts.getSessionId()));
        row.setAgentKey(trimToNull(facts.getAgentKey()));
        row.setAskedBy(trimToNull(facts.getAskedBy()));
        row.setPromptTokens(facts.getPromptTokens());
        row.setCompletionTokens(facts.getCompletionTokens());
        row.setReasoningTokens(facts.getReasoningTokens());
        row.setTotalTokens(facts.getTotalTokens());
        row.setToolCount(facts.getToolCount());
        row.setFinishReason(trimToNull(facts.getFinishReason()));
        row.setCostMs(facts.getCostMs());
        row.setCallAt(facts.getCallAtMs() == null ? null : new Date(facts.getCallAtMs()));
        row.setValid("1");
        return row;
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
