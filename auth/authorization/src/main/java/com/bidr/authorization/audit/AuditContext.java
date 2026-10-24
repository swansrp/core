package com.bidr.authorization.audit;

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
 * 外层若需自有上下文值参与求值，应在外层方法体内在调用内层之前完成（或内层不共用键名）；
 * 需要更复杂分层的场景走 sink 直调（programmatic）而非注解。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
public final class AuditContext {

    /** 约定键：旧值（切面自动并入 AuditRecord.oldValue → old_value 列） */
    public static final String OLD_VALUE = "oldValue";

    /** 约定键：新值（切面自动并入 AuditRecord.newValue → new_value 列） */
    public static final String NEW_VALUE = "newValue";

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
}
