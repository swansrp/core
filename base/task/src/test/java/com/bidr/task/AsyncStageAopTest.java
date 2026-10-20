package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 判据⑤的机器腿（§4.2.4-③ AOP 两坑）：① 注解打实现类、调用经接口 ⇒ 切点在目标类上解析，
 * 正向断言照常接管；② 同类内部自调用（this.xxx 不走代理）⇒ 切面不接管、什么都不写——
 * 负向测试钉住语义而不是假装它不存在。
 *
 * @author sharp
 */
class AsyncStageAopTest {

    /** 接口形态的流程（注解打实现类） */
    interface PortalFlow {

        StageOutcome intake(SaAsyncTask task, AsyncStageScope scope);
    }

    @AsyncTaskType(value = "IFACE_FLOW", maxRetry = 1)
    static class IfaceFlow implements PortalFlow {

        int runs = 0;

        @Override
        @AsyncStage(value = "INTAKE", order = 1)
        public StageOutcome intake(SaAsyncTask task, AsyncStageScope scope) {
            runs++;
            return StageOutcome.done();
        }
    }

    /** 带内部自调用入口的流程：caller() 里 this.intakeInternal(...) */
    @AsyncTaskType(value = "SELF_CALL_FLOW", maxRetry = 1)
    static class SelfCallFlow {

        int rawRuns = 0;

        @AsyncStage(value = "INNER", order = 1)
        public StageOutcome inner(SaAsyncTask task, AsyncStageScope scope) {
            rawRuns++;
            return StageOutcome.done();
        }

        /** 同类内部自调用入口（doc §⑤ 显式禁用的形状）：经代理调它，内部 this.inner 不走代理 */
        public StageOutcome caller(SaAsyncTask task, AsyncStageScope scope) {
            return inner(task, scope);
        }
    }

    /** 正向：注解在实现类方法上、调用经接口引用 → 切面照常接管（跑批驱动整链） */
    @Test
    @DisplayName("判据⑤-a：接口继承正向——注解在实现类、经接口调用仍被接管")
    void interfaceInheritanceStillIntercepted() {
        IfaceFlow flow = new IfaceFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        stack.service.submit("IFACE_FLOW", "ref-iface", null, "E1");
        stack.runner.tick();

        assertEquals("SUCCESS", stack.service.find("IFACE_FLOW", "ref-iface").getStatus(),
                "经接口调用（注册表在目标类解析注解）→ 切面接管记账");
        assertEquals(1, flow.runs);
    }

    /** 负向：同类内部自调用不走代理 → 切面不接管＝什么都不写（阶段体跑了但任务行不动） */
    @Test
    @DisplayName("判据⑤-b：自调用负向——this.xxx 不走代理，零记账")
    void selfInvocationWritesNothing() throws Exception {
        SelfCallFlow flow = new SelfCallFlow();
        TestStack stack = new TestStack(false, 1000, flow);
        SaAsyncTask task = stack.service.submit("SELF_CALL_FLOW", "ref-self", null, "E1");

        // 经代理调 caller()（自身无 @AsyncStage，不拦；内部 this.inner 是裸调用）
        Object proxy = stack.registry.require("SELF_CALL_FLOW").getBean();
        Method caller = proxy.getClass().getDeclaredMethod("caller", SaAsyncTask.class, AsyncStageScope.class);
        caller.setAccessible(true);
        caller.invoke(proxy, task, null);

        assertEquals(1, flow.rawRuns, "阶段体确实执行了");
        SaAsyncTask row = stack.service.find("SELF_CALL_FLOW", "ref-self");
        assertEquals("PENDING", row.getStatus(), "自调用不走代理：切面没接管，任务行零写入");
        assertEquals("INNER", row.getStage());
        assertThat(stack.repo.events).noneMatch(e -> e.startsWith("update-"));
    }
}
