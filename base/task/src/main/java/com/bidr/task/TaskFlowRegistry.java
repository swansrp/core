package com.bidr.task;

import com.bidr.task.dao.entity.SaAsyncTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 注解流程注册表（M16 棒 2）：收集带 {@link AsyncTaskType} 的 Bean，导出类型级参数
 * 与有序阶段清单，并把两件启动期校验钉死——阶段 {@code order} 断号/重复、阶段方法签名不符，
 * 一律 {@link IllegalStateException} 直接失败（判据⑥：不做运行时兜底）。
 *
 * <p>🔴 收集是<b>惰性</b>的（首次使用时才 {@code getBeansWithAnnotation}），触发点＝跑批的
 * {@code @PostConstruct}（启动期即校验、失败即启动失败）。不放在本类构造器的原因：流程 Bean
 * 是带切面的代理，代理链又依赖本注册表所在的装配链——构造期拉代理＝循环依赖，Boot 2.6 起
 * 默认拒绝循环引用、启动直接炸。惰性触发时各 Bean 均已构造完毕，环不存在。</p>
 *
 * <p>阶段方法在<b>目标类</b>上解析（注解打实现类、调用经接口也能命中——AOP 漏继承坑的对策），
 * 调用时经 Spring 代理 Bean 反射进入，让切面接管流程记账。</p>
 *
 * @author sharp
 */
@Service
public class TaskFlowRegistry {

    /** 单个阶段的导出（注解参数＋反射入口） */
    public static final class StageDef {

        private final String value;
        private final int order;
        private final String label;
        private final Method method;
        private final Class<? extends Throwable>[] noRetryOn;

        StageDef(String value, int order, String label, Method method, Class<? extends Throwable>[] noRetryOn) {
            this.value = value;
            this.order = order;
            this.label = label;
            this.method = method;
            this.noRetryOn = noRetryOn;
        }

        public String getValue() {
            return value;
        }

        public int getOrder() {
            return order;
        }

        /** 展示名（来自 {@code stageEnum} 的 label；未指到的阶段为 null） */
        public String getLabel() {
            return label;
        }

        public Method getMethod() {
            return method;
        }

        public Class<? extends Throwable>[] getNoRetryOn() {
            return noRetryOn;
        }
    }

    /** 单个任务类型（流程类）的导出 */
    public static final class FlowDef {

        private final String type;
        private final int maxRetry;
        private final long leaseMs;
        private final Object bean;
        private final List<StageDef> stages;
        private final Map<String, StageDef> byValue;

        FlowDef(String type, int maxRetry, long leaseMs, Object bean, List<StageDef> stages) {
            this.type = type;
            this.maxRetry = maxRetry;
            this.leaseMs = leaseMs;
            this.bean = bean;
            this.stages = Collections.unmodifiableList(stages);
            Map<String, StageDef> map = new LinkedHashMap<>();
            for (StageDef s : stages) {
                map.put(s.getValue(), s);
            }
            this.byValue = map;
        }

        public String getType() {
            return type;
        }

        public int getMaxRetry() {
            return maxRetry;
        }

        public long getLeaseMs() {
            return leaseMs;
        }

        /** 流程 Bean（Spring 代理）：阶段调用必须经它反射进入，切面才会接管 */
        public Object getBean() {
            return bean;
        }

        /** 有序阶段（order 升序，即 1..N） */
        public List<StageDef> getStages() {
            return stages;
        }

        public StageDef firstStage() {
            return stages.get(0);
        }

        public StageDef lastStage() {
            return stages.get(stages.size() - 1);
        }

        /** 按阶段标识取（未命中 null） */
        public StageDef stageOf(String stageValue) {
            return byValue.get(stageValue);
        }

        /** 阶段 → 进度百分比（0-100，按 order 推导；末阶段视作 100%） */
        public int progressOf(String stageValue) {
            if (stages.isEmpty()) {
                return 0;
            }
            int maxOrder = lastStage().getOrder();
            if (maxOrder <= 1) {
                return 100;
            }
            StageDef stage = stageOf(stageValue);
            if (stage == null) {
                return 0;
            }
            return Math.min(100, (stage.getOrder() - 1) * 100 / (maxOrder - 1));
        }
    }

    private final ApplicationContext context;

    private final long defaultLeaseMs;

    /** 已解析流程（惰性初始化；volatile＋双重检查，submit 与跑批两处触发都安全） */
    private volatile Map<String, FlowDef> flows;

    /** 生产构造：只存依赖不收集（🔴 收集放构造器会与流程代理成环，见类注释） */
    @Autowired
    public TaskFlowRegistry(ApplicationContext context,
                            @Value("${base.task.lease-ms:900000}") long defaultLeaseMs) {
        this.context = context;
        this.defaultLeaseMs = defaultLeaseMs;
    }

    /** 测试/装配构造：直接给定流程 Bean 集并立即解析校验 */
    public TaskFlowRegistry(Collection<?> flowBeans, long defaultLeaseMs) {
        this.context = null;
        this.defaultLeaseMs = defaultLeaseMs;
        this.flows = Collections.unmodifiableMap(parse(flowBeans, defaultLeaseMs));
    }

    private Map<String, FlowDef> flows() {
        Map<String, FlowDef> parsed = flows;
        if (parsed != null) {
            return parsed;
        }
        synchronized (this) {
            if (flows == null) {
                flows = Collections.unmodifiableMap(parse(context.getBeansWithAnnotation(AsyncTaskType.class).values(), defaultLeaseMs));
            }
            return flows;
        }
    }

    /** 全部已注册类型（跑批遍历用；首次调用触发收集与启动期校验） */
    public Collection<FlowDef> all() {
        return flows().values();
    }

    /** 按类型取（未注册抛——跑批与受理只处理注册类型） */
    public FlowDef require(String taskType) {
        FlowDef def = flows().get(taskType);
        if (def == null) {
            throw new IllegalStateException("未注册的异步任务类型: " + taskType);
        }
        return def;
    }

    /** 按类型取（未注册 null） */
    public FlowDef find(String taskType) {
        return flows().get(taskType);
    }

    // ==================== 解析与校验 ====================

    private static Map<String, FlowDef> parse(Collection<?> flowBeans, long defaultLeaseMs) {
        Map<String, FlowDef> map = new LinkedHashMap<>();
        for (Object bean : flowBeans) {
            FlowDef def = parseOne(bean, defaultLeaseMs);
            FlowDef existed = map.get(def.getType());
            if (existed != null) {
                throw new IllegalStateException("异步任务类型重复注册: type=" + def.getType()
                        + " classes=" + existed.getBean().getClass().getName() + ","
                        + bean.getClass().getName());
            }
            map.put(def.getType(), def);
        }
        return map;
    }

    /** 解析单个流程 Bean：读类注解＋方法注解，做断号/重复/签名校验 */
    private static FlowDef parseOne(Object bean, long defaultLeaseMs) {
        Class<?> targetClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);
        AsyncTaskType type = AnnotatedElementUtils.findMergedAnnotation(targetClass, AsyncTaskType.class);
        if (type == null) {
            throw new IllegalStateException("流程类缺 @AsyncTaskType: " + targetClass.getName());
        }
        Map<String, String> labels = labelsOf(type.stageEnum());
        List<StageDef> stages = new ArrayList<>();
        ReflectionUtils.doWithMethods(targetClass, method -> {
            AsyncStage stage = AnnotatedElementUtils.findMergedAnnotation(method, AsyncStage.class);
            if (stage == null) {
                return;
            }
            verifySignature(targetClass, method);
            stages.add(new StageDef(stage.value(), stage.order(),
                    labels.get(stage.value()), method, stage.noRetryOn()));
        });
        if (stages.isEmpty()) {
            throw new IllegalStateException("流程类没有任何 @AsyncStage 方法: " + targetClass.getName());
        }
        stages.sort(Comparator.comparingInt(StageDef::getOrder));
        // 🔴 order 必须从 1 连续：断号/重复＝启动期直接失败（进度按 order 推导，失真不可兜底）
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getOrder() != i + 1) {
                throw new IllegalStateException("阶段 order 必须从 1 连续（断号或重复）: type=" + type.value()
                        + " class=" + targetClass.getName()
                        + " orders=" + ordersOf(stages));
            }
        }
        for (int i = 1; i < stages.size(); i++) {
            for (int j = 0; j < i; j++) {
                if (stages.get(i).getValue().equals(stages.get(j).getValue())) {
                    throw new IllegalStateException("阶段标识重复: type=" + type.value()
                            + " stage=" + stages.get(i).getValue());
                }
            }
        }
        long leaseMs = type.leaseMs() > 0 ? type.leaseMs() : defaultLeaseMs;
        return new FlowDef(type.value(), type.maxRetry(), leaseMs, bean, stages);
    }

    /** 阶段方法签名定死：StageOutcome xxx(SaAsyncTask, AsyncStageScope) */
    private static void verifySignature(Class<?> targetClass, Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length != 2 || !SaAsyncTask.class.equals(params[0])
                || !AsyncStageScope.class.equals(params[1])
                || !StageOutcome.class.equals(method.getReturnType())) {
            throw new IllegalStateException("阶段方法签名须为 StageOutcome xxx(SaAsyncTask, AsyncStageScope): "
                    + targetClass.getName() + "#" + method.getName());
        }
    }

    /** stageEnum → 阶段标识 → label（未指定或未命中返回空表） */
    private static Map<String, String> labelsOf(Class<? extends AsyncStageEnum> stageEnum) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (stageEnum == null || AsyncStageEnum.class.equals(stageEnum)) {
            return labels;
        }
        if (!stageEnum.isEnum()) {
            throw new IllegalStateException("stageEnum 必须是枚举: " + stageEnum.getName());
        }
        for (AsyncStageEnum constant : stageEnum.getEnumConstants()) {
            labels.put(constant.getValue(), constant.getLabel());
        }
        return labels;
    }

    private static List<Integer> ordersOf(List<StageDef> stages) {
        List<Integer> orders = new ArrayList<>();
        for (StageDef s : stages) {
            orders.add(s.getOrder());
        }
        return orders;
    }
}
