package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 纪律 §4.2.4 的机器腿：
 * ① 回滚腿（M-10）——切面在事务拦截器<b>外层</b>且记账写在业务事务之外：阶段方法内手工
 *    setRollbackOnly，断言 stage 仍已推进落库、且推进的写发生在业务回滚决策之后；
 * ④ 载体结构腿——{@link AsyncStageScope} 公开面只有三个写口、字段无 ThreadLocal/集合黑板；
 * ⑤ 未标注零影响腿——类上没有 @AsyncTaskType ⇒ 不注册、零任务底盘行为。
 *
 * @author sharp
 */
class AsyncTaskDisciplineTest {

    /** 事务事件与库写入共用的流水（断言「先后顺序」） */
    private final List<String> shared = new ArrayList<>();

    /** 极简事务管理器：记录 begin/commit/rollback（rollback-only 的 commit 走 rollback，不抛） */
    private final PlatformTransactionManager txManager = new PlatformTransactionManager() {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            shared.add("tx-begin");
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            shared.add(status.isRollbackOnly() ? "tx-rollback" : "tx-commit");
        }

        @Override
        public void rollback(TransactionStatus status) {
            shared.add("tx-rollback");
        }
    };

    /** 阶段体自带手工回滚的流程（纪律①的靶子：业务事务回滚不得抹掉进度推进） */
    @AsyncTaskType(value = "TX_FLOW", maxRetry = 1)
    static class RollbackStageFlow {

        final List<String> visited = new ArrayList<>();

        @AsyncStage(value = "ONE", order = 1)
        public StageOutcome one(SaAsyncTask task, AsyncStageScope scope) {
            visited.add("ONE");
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return StageOutcome.to("TWO");
        }

        @AsyncStage(value = "TWO", order = 2)
        public StageOutcome two(SaAsyncTask task, AsyncStageScope scope) {
            visited.add("TWO");
            return StageOutcome.done();
        }
    }

    /**
     * 纪律①（M-10 判据）：切面 @Order(0) 在事务拦截器（LOWEST_PRECEDENCE＝最内层）外层——
     * 阶段方法手工 setRollbackOnly 后业务事务走 rollback，而切面的 stage 推进在 proceed()
     * 返回之后独立落库。断言（a）stage 已推进；（b）update-stage 事件在 tx-rollback 之后
     * （若切面被排进事务内层，推进写会发生在回滚决策之前、并随业务事务一起被抹掉）。
     */
    @Test
    @DisplayName("纪律①回滚腿：阶段内手工回滚，stage 仍推进落库且推进写在回滚决策之后")
    void stageAdvancesDespiteBusinessRollback() throws Exception {
        RollbackStageFlow flow = new RollbackStageFlow();
        InMemoryAsyncTaskRepository repo = new InMemoryAsyncTaskRepository() {
            @Override
            public boolean updateStage(Long id, String owner, String stage) {
                shared.add("update-stage:" + stage);
                return super.updateStage(id, owner, stage);
            }
        };
        TaskContextBridge bridge = new TaskContextBridge();
        TaskWakeSignal wake = new TaskWakeSignal(false, Runnable::run);
        List<AsyncTaskObserver> observers = new ArrayList<>();

        // 代理：切面（@Order(0)＝外层）＋事务拦截器（LOWEST_PRECEDENCE＝内层）——对齐生产次序
        TransactionInterceptor txAdvice = new TransactionInterceptor(txManager,
                new MatchAlwaysTransactionAttributeSource());
        AspectJProxyFactory factory = new AspectJProxyFactory(flow);
        factory.setProxyTargetClass(true);
        AsyncStageAspect aspect = new AsyncStageAspect();
        factory.addAspect(aspect);
        factory.addAdvice(txAdvice);
        Object proxy = factory.getProxy();

        TaskFlowRegistry registry = new TaskFlowRegistry(Collections.singletonList(proxy), 900_000L);
        AsyncTaskService service = new AsyncTaskService(repo, registry, wake, bridge, observers, 1000);
        aspect.wire(registry, service);

        SaAsyncTask task = service.submit("TX_FLOW", "ref-tx", null, "E1");
        // 执行侧写口带 owner＋RUNNING 谓词（A3）：先按跑批的抢占步拿到持有权，再经代理调阶段
        SaAsyncTask claimed = repo.claimBatch("TX_FLOW", "owner-tx",
                new java.util.Date(System.currentTimeMillis() + 900_000L), 1).get(0);

        Method one = RollbackStageFlow.class.getDeclaredMethod("one", SaAsyncTask.class, AsyncStageScope.class);
        one.setAccessible(true);
        one.invoke(proxy, claimed, null);

        // （a）业务回滚了，stage 仍推进落库（「任务从没跑过」的双实例重跑老根被堵住）
        assertEquals(Collections.singletonList("ONE"), flow.visited);
        assertEquals("TWO", service.find("TX_FLOW", "ref-tx").getStage(),
                "业务事务回滚不得抹掉进度推进（切面在事务之外独立落库）");

        // （b）顺序：tx-begin → tx-rollback → update-stage
        int begin = shared.indexOf("tx-begin");
        int rollback = shared.indexOf("tx-rollback");
        int advance = shared.indexOf("update-stage:TWO");
        assertThat(begin).isGreaterThanOrEqualTo(0);
        assertThat(rollback).isGreaterThan(begin);
        assertThat(advance).isGreaterThan(rollback);
        assertThat(task.getId()).isNotNull();
    }

    /** 纪律④：阶段作用域公开面只有三个写口，字段无 ThreadLocal/集合——无内存黑板 */
    @Test
    @DisplayName("纪律④载体腿：AsyncStageScope 只有三个写口，无 ThreadLocal/集合黑板")
    void scopeExposesOnlyWriteThroughMethods() {
        List<String> publicMethods = new ArrayList<>();
        for (Method m : AsyncStageScope.class.getDeclaredMethods()) {
            if (Modifier.isPublic(m.getModifiers())) {
                publicMethods.add(m.getName());
            }
        }
        assertThat(publicMethods).containsExactlyInAnyOrder("snapshot", "claimExternal", "externalId",
                "releaseExternal");
        for (java.lang.reflect.Field field : AsyncStageScope.class.getDeclaredFields()) {
            Class<?> type = field.getType();
            assertThat(ThreadLocal.class.isAssignableFrom(type))
                    .as("作用域不得持 ThreadLocal（内存黑板）: " + field.getName()).isFalse();
            assertThat(List.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type))
                    .as("作用域不得持集合载体: " + field.getName()).isFalse();
        }
        assertThat(StageOutcome.class.getDeclaredFields()).hasSize(3);
    }

    /** 纪律⑤：类上没有 @AsyncTaskType ⇒ 注册表不收、submit 拒——未标注零影响 */
    @Test
    @DisplayName("纪律⑤未标注腿：无 @AsyncTaskType 的类不注册，零任务底盘行为")
    void unannotatedClassHasNoEffect() {
        class PlainFlow {
            @AsyncStage(value = "A", order = 1)
            public StageOutcome a(SaAsyncTask task, AsyncStageScope scope) {
                return StageOutcome.done();
            }
        }
        TaskFlowRegistry registry = new TaskFlowRegistry(
                Collections.singletonList(new TestStack.ThreeStageFlow()), 900_000L);
        assertThat(registry.all()).hasSize(1);
        assertThat(registry.find("PLAIN")).isNull();
        AsyncTaskService service = new AsyncTaskService(new InMemoryAsyncTaskRepository(), registry,
                new TaskWakeSignal(false, Runnable::run), new TaskContextBridge(), new ArrayList<>(), 1000);
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> service.submit("PLAIN", "r", null, "E1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未注册");
    }
}
