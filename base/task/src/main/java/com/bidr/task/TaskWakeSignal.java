package com.bidr.task;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.PreDestroy;

/**
 * 提交即唤醒信号（M16 棒 2）：{@code submit} 落库后发一次进程内信号，runner 立刻跑该类型一轮。
 *
 * <p>🔴 它只是<b>加速器、不是正确性依赖</b>：信号丢了自然由跑批的 {@code fixedDelay} 轮询兜底
 * （判据⑧第二条腿钉住）；抢占仍是条件 UPDATE、多实例纪律不变。实现即「尽力而为投递一个任务类型
 * 给监听者」——监听者抛异常吞掉记日志，不影响提交线程。</p>
 *
 * @author sharp
 */
@Service
public class TaskWakeSignal {

    /** 监听者：runner 注册自己（收到类型即 drain 一轮） */
    private volatile Consumer<String> listener;

    private final Executor executor;

    private final AtomicBoolean enabled;

    /** 生产构造：单线程守护池（异步投递，提交线程不等跑批） */
    @Autowired
    public TaskWakeSignal(@Value("${base.task.wake.enabled:true}") boolean wakeEnabled) {
        this(wakeEnabled, Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "base-task-wake");
            t.setDaemon(true);
            return t;
        }));
    }

    /** 装配/测试构造：自定义执行器（测试用同步执行器＝投递即执行，可断言） */
    public TaskWakeSignal(boolean enabled, Executor executor) {
        this.enabled = new AtomicBoolean(enabled);
        this.executor = executor;
    }

    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    /** 发信号（submit 落库后调）：尽力而为，任何异常不向提交线程抛 */
    public void wake(String taskType) {
        if (!enabled.get() || executor == null) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    Consumer<String> l = listener;
                    if (l != null) {
                        l.accept(taskType);
                    }
                } catch (Exception e) {
                    // 信号只是加速器：监听侧失败由轮询兜底，不向任何人抛
                    org.slf4j.LoggerFactory.getLogger(TaskWakeSignal.class)
                            .debug("唤醒信号处理失败（将由轮询兜底）: type={} {}", taskType, e.getMessage());
                }
            });
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(TaskWakeSignal.class)
                    .debug("唤醒信号投递失败（将由轮询兜底）: type={} {}", taskType, e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (executor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) executor).shutdown();
        }
    }
}
