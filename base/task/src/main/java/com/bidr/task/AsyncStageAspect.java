package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * 阶段切面（M16 棒 2 注解通道的核心）：拦 {@code @AsyncStage} 方法，把「推进与落库」全部接管——
 * 业务只写「这一步干什么」，不写「把 stage 推到下一档、把外部 id 回写、这轮先不做」。
 *
 * <p>接管面：三态返回的落库（to/pending/done）、异常分诊（{@link RetryAware}／
 * {@code noRetryOn}：可重试＝FAILED＋退避、预算耗尽＝DEAD、不可重试＝直接 DEAD——🔴 这条必须保住，
 * 静默兜底会把「暂时没页」固化成「永远没页」）、终态触发观察者（SUCCESS/DEAD 必发、FAILED 不发）。</p>
 *
 * <p>🔴 纪律 §4.2.4-①：本切面 {@code @Order(0)} 排在事务拦截器（缺省 LOWEST_PRECEDENCE＝最内层）
 * <b>外层</b>，且记账写在 {@code proceed()} 返回之后——阶段方法自带 {@code @Transactional}
 * 回滚时，进度推进已在业务事务之外独立落库（判据腿＝阶段方法内手工回滚、断言 stage 已推进）。</p>
 *
 * <p>🔴 AOP 两坑（纪律 §4.2.4-③）：注解打实现类、经接口调用→切点在目标类上解析（注册表同理）；
 * 同类内部自调用（{@code this.xxx(...)}）不走代理、切面不接管＝什么都不写——doc 显式禁用，
 * 负向测试钉住语义而不是假装它不存在。阶段调用必须经 Spring 代理 Bean（跑批入口天然满足）。</p>
 *
 * @author sharp
 */
@Aspect
@Order(0)
@Component
public class AsyncStageAspect {

    private static final Logger log = LoggerFactory.getLogger(AsyncStageAspect.class);

    private TaskFlowRegistry registry;

    private AsyncTaskService taskService;

    @Autowired
    public AsyncStageAspect(TaskFlowRegistry registry, AsyncTaskService taskService) {
        wire(registry, taskService);
    }

    /** 测试装配口：先代理流程 Bean、后建注册表/服务的次序需要（生产走构造器注入） */
    AsyncStageAspect() {
    }

    void wire(TaskFlowRegistry registry, AsyncTaskService taskService) {
        this.registry = registry;
        this.taskService = taskService;
    }

    @Around("@annotation(asyncStage)")
    public Object aroundStage(ProceedingJoinPoint pjp, AsyncStage asyncStage) throws Throwable {
        SaAsyncTask task = taskOf(pjp);
        if (task == null) {
            // 不带任务行的调用不是跑批入口：不接管、原样放行（防误拦业务复用阶段体内部逻辑）
            return pjp.proceed();
        }
        TaskFlowRegistry.FlowDef flow = registry.require(task.getTaskType());
        TaskFlowRegistry.StageDef stageDef = flow.stageOf(asyncStage.value());
        if (stageDef == null) {
            // 结构错（注解值不在流程清单内）：重试无意义的代码错，直接 DEAD 而不是交给补偿扫描
            AsyncTaskService.StageContractException structural = new AsyncTaskService.StageContractException(
                    "阶段标识不在流程清单内: type=" + flow.getType() + " stage=" + asyncStage.value());
            log.error("阶段注解与流程清单不一致，任务置 DEAD: type={} task={} {}",
                    flow.getType(), task.getId(), structural.getMessage());
            taskService.dead(task, structural);
            return null;
        }
        AsyncStageScope scope = new AsyncStageScope(taskService, task);
        // 🔘 业务事务在这一层之内：proceed 返回（正常或异常经分诊消化）之后的记账全部在业务事务之外
        StageOutcome outcome;
        try {
            Object result = pjp.proceed(new Object[]{task, scope});
            outcome = result instanceof StageOutcome ? (StageOutcome) result : StageOutcome.done();
        } catch (Exception e) {
            // 只吃 Exception：Error（OOM/栈溢出）原样上抛，不被误判成业务失败
            diagnose(task, flow, stageDef, e);
            // 异常已分诊落库（FAILED/DEAD），不向调用方重抛：跑批只关心任务行状态
            return null;
        }
        // 🔴 三态记账纳入分诊（M16 修复轮 A4）：结构错＝DEAD；基础设施错只记日志、任务行不动
        try {
            switch (outcome.getKind()) {
                case ADVANCE:
                    taskService.advance(task, flow, outcome.getTargetStage());
                    break;
                case PENDING:
                    taskService.pending(task, flow, outcome.getBackoffMs());
                    break;
                case DONE:
                    taskService.succeed(task, flow);
                    log.info("任务完成: type={} task={} bizRef={}", task.getTaskType(), task.getId(), task.getBizRef());
                    break;
                default:
                    throw new AsyncTaskService.StageContractException("未知三态: " + outcome.getKind());
            }
        } catch (AsyncTaskService.StageContractException e) {
            // 结构错：不可重试、观察者发一次 DEAD——不留给补偿扫描按租约重跑同一阶段体
            log.error("阶段记账契约错误，任务置 DEAD: type={} task={} stage={} 错误={}",
                    task.getTaskType(), task.getId(), task.getStage(), e.getMessage());
            taskService.dead(task, e);
        } catch (Exception e) {
            // 非结构类（数据库/基础设施抖动）：任务行不动，租约到期由补偿扫描回收——
            // 不要把 DB 抖动误判成业务死（DEAD 不可重试）
            log.error("阶段记账异常（任务行不动，租约到期由补偿扫描回收）: type={} task={} stage={} {}",
                    task.getTaskType(), task.getId(), task.getStage(), e.getMessage(), e);
        }
        return null;
    }

    /** 异常即终态判据：RetryAware 两半语义＋noRetryOn 类型补口＋重试预算 */
    private void diagnose(SaAsyncTask task, TaskFlowRegistry.FlowDef flow,
                          TaskFlowRegistry.StageDef stageDef, Throwable e) {
        if (!retryable(stageDef, e)) {
            log.warn("任务不可重试失败，直接 DEAD: type={} task={} stage={} 错误={}",
                    task.getTaskType(), task.getId(), task.getStage(), e.getMessage());
            taskService.dead(task, e);
            return;
        }
        if (AsyncTaskService.retryExhausted(flow, task)) {
            taskService.dead(task, e);
            return;
        }
        taskService.fail(task, flow, e);
        log.warn("任务失败待重试: type={} task={} stage={} attempt={} 错误={}",
                task.getTaskType(), task.getId(), task.getStage(), task.getAttempt(), e.getMessage());
    }

    /** RetryAware 布尔形状照抄：未实现口＝默认可重试（静默兜底被禁）；noRetryOn 命中＝不可重试 */
    private static boolean retryable(TaskFlowRegistry.StageDef stageDef, Throwable e) {
        for (Class<? extends Throwable> type : stageDef.getNoRetryOn()) {
            if (type.isInstance(e)) {
                return false;
            }
        }
        if (e instanceof RetryAware) {
            return ((RetryAware) e).isRetryable();
        }
        return true;
    }

    /** 从连接点取任务行参数（阶段方法签名由注册表钉死：首位是 SaAsyncTask） */
    private static SaAsyncTask taskOf(ProceedingJoinPoint pjp) {
        for (Object arg : pjp.getArgs()) {
            if (arg instanceof SaAsyncTask) {
                return (SaAsyncTask) arg;
            }
        }
        return null;
    }

    /** 当前方法上的阶段注解（供调试/守卫测试定位；合并注解语义与注册表一致） */
    static AsyncStage annotationOf(Method method) {
        return AnnotatedElementUtils.findMergedAnnotation(method, AsyncStage.class);
    }
}
