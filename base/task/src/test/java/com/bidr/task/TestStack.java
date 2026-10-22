package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 核心底盘测试的装配腿（测试件，无 @Test）：内存库＋真实注册表/服务/桥/切面/跑批，
 * 流程 Bean 经 {@link AspectJProxyFactory} 代理（CGLIB，对齐生产 proxyTargetClass 形态）
 * 后注册——判据①「测试代码零次手工 update」的机器形态：一切推进只经底盘与切面。
 *
 * <p>装配次序（与生产无环约束一致）：裸切面→代理流程→注册表→服务→回填切面→跑批。</p>
 *
 * @author sharp
 */
final class TestStack {

    final InMemoryAsyncTaskRepository repo;

    final TaskWakeSignal wake;

    final TaskContextBridge bridge = new TaskContextBridge();

    final TaskFlowRegistry registry;

    final AsyncTaskService service;

    final AsyncTaskRunner runner;

    final List<String> observerEvents = new ArrayList<>();

    /** 阶段迁移事件流水（onStage 契约腿断言用）：形如 {@code FIRST->SECOND:ref-1} */
    final List<String> stageEvents = new ArrayList<>();

    private final List<AsyncTaskObserver> observers = new ArrayList<>();

    TestStack(boolean wakeEnabled, int maxQueueAhead, Object firstFlow, Object... moreFlows) {
        this(wakeEnabled, maxQueueAhead, new InMemoryAsyncTaskRepository(), firstFlow, moreFlows);
    }

    /** 可注入仓储的装配（并发撞唯一键腿：insert 行为要被替换） */
    TestStack(boolean wakeEnabled, int maxQueueAhead, InMemoryAsyncTaskRepository repository,
              Object firstFlow, Object... moreFlows) {
        this.repo = repository;
        // 同步执行器＝唤醒即 drain（可断言）；wakeEnabled=false 即「掐掉信号」形态
        this.wake = new TaskWakeSignal(wakeEnabled, Runnable::run);
        AsyncStageAspect aspect = new AsyncStageAspect();
        List<Object> proxies = new ArrayList<>();
        for (Object flow : join(firstFlow, moreFlows)) {
            AspectJProxyFactory factory = new AspectJProxyFactory(flow);
            factory.setProxyTargetClass(true);
            factory.addAspect(aspect);
            proxies.add(factory.getProxy());
        }
        this.registry = new TaskFlowRegistry(proxies, 900_000L);
        this.service = new AsyncTaskService(repo, registry, wake, bridge, observers, maxQueueAhead);
        aspect.wire(registry, service);
        this.runner = new AsyncTaskRunner(registry, repo, service, bridge, wake);
        this.runner.clock = repo::clock;
    }

    private static List<Object> join(Object first, Object[] more) {
        List<Object> all = new ArrayList<>();
        all.add(first);
        if (more != null) {
            all.addAll(java.util.Arrays.asList(more));
        }
        return all;
    }

    /** 追加观察者（服务持的是同一可变列表，通知时逐件遍历） */
    void addObserver(AsyncTaskObserver observer) {
        observers.add(observer);
    }

    /** 记录型观察者（打到 observerEvents：形如 SUCCESS:ref-1） */
    AsyncTaskObserver recordingObserver() {
        return (task, status) -> observerEvents.add(status.name() + ":" + task.getBizRef());
    }

    /**
     * 阶段迁移记录型观察者（打到 stageEvents：形如 {@code FIRST->SECOND:ref-1}）：两口都显式
     * 实现——与上面只实现 onTerminal 的 lambda 版互为印证：lambda 能编译本身就是 default 空实现
     * （不实现 onStage 也不炸）的机器证明。
     */
    AsyncTaskObserver stageRecordingObserver() {
        return new AsyncTaskObserver() {
            @Override
            public void onTerminal(SaAsyncTask task, AsyncTaskStatus status) {
                observerEvents.add(status.name() + ":" + task.getBizRef());
            }

            @Override
            public void onStage(SaAsyncTask task, String fromStage, String toStage) {
                stageEvents.add(fromStage + "->" + toStage + ":" + task.getBizRef());
            }
        };
    }

    /** 可控三阶段流程（第二阶段行为可换：to/pending/抛异常），阶段体零手工记账 */
    @AsyncTaskType(value = ThreeStageFlow.TYPE, maxRetry = 2)
    static class ThreeStageFlow {

        static final String TYPE = "THREE_STAGE";

        final List<String> visited = new ArrayList<>();

        volatile Supplier<StageOutcome> secondOutcome = () -> StageOutcome.to("THIRD");

        String bizRefSeenInSecondStage;

        @AsyncStage(value = "FIRST", order = 1)
        public StageOutcome first(SaAsyncTask task, AsyncStageScope scope) {
            visited.add("FIRST:" + task.getBizRef());
            return StageOutcome.to("SECOND");
        }

        @AsyncStage(value = "SECOND", order = 2)
        public StageOutcome second(SaAsyncTask task, AsyncStageScope scope) {
            visited.add("SECOND");
            bizRefSeenInSecondStage = task.getBizRef();
            return secondOutcome.get();
        }

        @AsyncStage(value = "THIRD", order = 3)
        public StageOutcome third(SaAsyncTask task, AsyncStageScope scope) {
            visited.add("THIRD");
            return StageOutcome.done();
        }
    }
}
