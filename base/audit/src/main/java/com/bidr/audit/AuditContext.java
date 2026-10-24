package com.bidr.audit;

import java.util.HashMap;
import java.util.Map;

/**
 * 审计运行时上下文（ThreadLocal 逃生口）：{@code @AuditTrail} 方法体内手动补充
 * 注解写不下的运行时值——典型如查库才知道的旧值/流水号。切面在方法成功返回后组装
 * {@link AuditRecord} 时，把本上下文快照暴露给 SpEL（{@code #ctx.get('key')}），
 * 并自动并入 {@link #OLD_VALUE}/{@link #NEW_VALUE} 两个约定键到对应审计列。
 *
 * <p><b>生命周期</b>：方法作用域——{@link AuditTrailAspect} 在被注方法收尾（finally）
 * 时 {@link #clear()}，请求线程侧另有 {@code AuthInterceptor#afterCompletion} 兜底清理
 * （覆盖"注解方法被内部调用、切面不生效"的盲区残留）。Tomcat 线程池复用，污染即串档，
 * 用完即弃、不跨方法假设残留。</p>
 *
 * <p><b>嵌套口径（实现取简）</b>：嵌套审计方法共享同一线程上下文，最内层收尾即整表清空——
 * 外层<b>此前</b> put 的约定键随之丢失。外层若需自有值参与求值，有效时机只有「最后一次
 * 内层调用返回之后、外层返回之前」（即内层清空后重新 put）；需要更复杂分层的场景走 sink
 * 直调（programmatic）而非注解。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
public final class AuditContext {

    /** 约定键：旧值（切面自动并入 AuditRecord.oldValue → old_value 列） */
    public static final String OLD_VALUE = "oldValue";

    /** 约定键：新值（切面自动并入 AuditRecord.newValue → new_value 列） */
    public static final String NEW_VALUE = "newValue";

    /** 约定键：资源简述（切面并入 AuditRecord.resourceLabel，<b>优先于注解 resourceLabel 属性</b>） */
    public static final String LABEL = "label";

    /** 约定键：业务语义详情（切面并入 AuditRecord.detail，<b>优先于注解 detail 属性</b>；多次追加分号连接） */
    public static final String DETAIL = "detail";

    /** 约定键：动作覆写（切面并入 AuditRecord.action，<b>优先于注解 action 属性</b>——运行时才能定档的场景） */
    public static final String ACTION_OVERRIDE = "actionOverride";

    /** 约定键：分组键覆写（切面并入 AuditRecord.groupKey，<b>优先于注解 group 属性与 resolver</b>——如建空间后才知道新 id） */
    public static final String GROUP_OVERRIDE = "groupOverride";

    /** 约定键：资源标识覆写（切面并入 AuditRecord.bizId，<b>优先于注解 bizId 属性与三级提取</b>——新建面 id 只在返回值里的场景） */
    public static final String RESOURCE_ID_OVERRIDE = "resourceIdOverride";

    private static final ThreadLocal<Map<String, Object>> HOLDER = new ThreadLocal<>();

    private AuditContext() {
    }

    /**
     * 放入/覆写一个运行时上下文值（方法体内调用；value 允许 null——存 null 等价移除该键，
     * 与 Map 语义对齐，防 SpEL 侧出现"有键无值"歧义）。
     */
    public static void put(String key, Object value) {
        if (key == null) {
            return;
        }
        Map<String, Object> map = HOLDER.get();
        if (map == null) {
            map = new HashMap<>();
            HOLDER.set(map);
        }
        if (value == null) {
            map.remove(key);
        } else {
            map.put(key, value);
        }
    }

    /**
     * 取一个运行时上下文值；键不存在（或本线程从未 put）返回 null。
     */
    public static Object get(String key) {
        Map<String, Object> map = HOLDER.get();
        return map == null ? null : map.get(key);
    }

    /**
     * 当前上下文快照（不可变拷贝）——切面求值用：暴露为 SpEL 变量 {@code #ctx}，
     * 并提取约定键入审计行。外部调用得到的是拷贝，后续 put 不影响已取快照。
     */
    public static Map<String, Object> snapshot() {
        Map<String, Object> map = HOLDER.get();
        if (map == null || map.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return java.util.Collections.unmodifiableMap(new HashMap<>(map));
    }

    /**
     * 清空当前线程上下文（切面 finally 收尾＋拦截器兜底都用它；未 put 过调用也安全）。
     */
    public static void clear() {
        Map<String, Object> map = HOLDER.get();
        if (map != null) {
            map.clear();
            HOLDER.remove();
        }
    }

    // ==================== 类型化便捷方法（约定键门面；core 不含业务值域，值类型一律 String） ====================

    /**
     * 设置当前审计行的资源简述（如上传文件名/页面名——人话文案源）。
     * 后写覆盖前写；null/空白静默忽略。
     */
    public static void label(String text) {
        if (notBlank(text)) {
            put(LABEL, text.trim());
        }
    }

    /**
     * 追加一条业务语义详情到当前审计行（"做了什么"的人话补充）。
     * 多次调用按调用顺序以中文分号连接；null/空白静默忽略。
     */
    public static void detail(String text) {
        if (!notBlank(text)) {
            return;
        }
        Object existing = get(DETAIL);
        put(DETAIL, existing == null ? text.trim() : existing + "；" + text.trim());
    }

    /**
     * 运行时覆写动作（切面静态解析不出实际动作用途时，如一个接口兼做两种动作）。
     * 值域归宿主（core 不定义）；null 忽略。
     */
    public static void actionOverride(String action) {
        if (notBlank(action)) {
            put(ACTION_OVERRIDE, action.trim());
        }
    }

    /**
     * 运行时覆写分组键（注解/resolver 都取不到时，如按 requestId 审批后 Service 从实体取空间）。
     * null 忽略。
     */
    public static void groupOverride(String groupKey) {
        if (notBlank(groupKey)) {
            put(GROUP_OVERRIDE, groupKey.trim());
        }
    }

    /**
     * 运行时覆写资源标识（新建类写面的 id 只在返回值里，SpEL/三级提取结构性拿不到时）。
     * null/空白忽略。
     */
    public static void resourceIdOverride(String resourceId) {
        if (notBlank(resourceId)) {
            put(RESOURCE_ID_OVERRIDE, resourceId.trim());
        }
    }

    private static boolean notBlank(String text) {
        return text != null && !text.trim().isEmpty();
    }
}
