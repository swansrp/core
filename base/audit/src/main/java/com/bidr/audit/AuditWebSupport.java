package com.bidr.audit;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 审计 web 取数/判定静态段（包私有）：默认拦截切面（{@link AuditTrailAutoAdvisor}）的
 * 判定与提取工具，显式注解切面只复用端点提取。全部静态纯函数、绝不抛错（审计链路纪律：
 * 任何取数失败＝返回 null/false，不炸业务）。
 *
 * <p>id 三级提取与路径变量对齐的实现沿 inkhub 真库验证版泛化（同名绑定入参→路径模板
 * 变量→第一个入参 {@code getId()} 反射；复杂对象不做 id 源、不反射转文本，规避泄密）——
 * 三级提取只服务默认拦截模式（有请求面），显式注解模式不走。</p>
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
public final class AuditWebSupport {

    private AuditWebSupport() {
    }

    // ==================== 请求上下文取数 ====================

    /** 当前请求（无 web 上下文返回 null，如定时任务/异步线程） */
    static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    /** best-match 处理器（非 MVC 分发面返回 null） */
    static HandlerMethod resolveHandlerMethod(HttpServletRequest request) {
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        return handler instanceof HandlerMethod ? (HandlerMethod) handler : null;
    }

    /** 请求路径（去 contextPath 的 pathInfo 优先，回落 requestURI） */
    static String requestPath(HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            path = request.getServletPath();
        }
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI();
        }
        return path;
    }

    /** 来源端点 "METHOD path"（溯源；无请求面 null） */
    static String endpointOf(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return request.getMethod() + " " + requestPath(request);
    }

    // ==================== 判定 ====================

    /** 写动词判定：HTTP method 在配置写动词集内（大小写不敏感比对，配置值约定大写） */
    static boolean isWriteMethod(String httpMethod, Set<String> writeMethods) {
        if (httpMethod == null) {
            return false;
        }
        return writeMethods.contains(httpMethod.toUpperCase());
    }

    /**
     * 读语义判定：请求路径按「路径段序列包含」匹配任一白名单尾段（段级精确）。
     * kernel 透视面映射为 {@code {"/pivot", "/pivot/**"}}——子串 contains 会把
     * {@code /pivotSetting/update} 这类同名前缀写端点误判成读；段级匹配只认完整段。
     * 尾段首尾斜杠可有可无（{@code "/general/"} 与 {@code "general"} 等价）。
     */
    public static boolean isReadPath(String path, List<String> readPathTails) {
        if (path == null) {
            return false;
        }
        String[] pathSegs = segments(path);
        for (String tail : readPathTails) {
            String[] tailSegs = segments(tail);
            if (tailSegs.length > 0 && containsSequence(pathSegs, tailSegs)) {
                return true;
            }
        }
        return false;
    }

    /** 按 '/' 切段并丢弃空段（首尾/重复斜杠形态归一） */
    private static String[] segments(String path) {
        List<String> out = new ArrayList<>();
        for (String seg : path.split("/")) {
            if (!seg.isEmpty()) {
                out.add(seg);
            }
        }
        return out.toArray(new String[0]);
    }

    /** tail 段序列是否作为连续子序列出现在 path 段序列中 */
    private static boolean containsSequence(String[] path, String[] tail) {
        outer:
        for (int i = 0; i + tail.length <= path.length; i++) {
            for (int j = 0; j < tail.length; j++) {
                if (!path[i + j].equals(tail[j])) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 通用动作推导（无显式 action 时的兜底，产出通用 CRUD 大写动词，宿主 sink 自行映射值域）：
     * 路径含 /insert、/import/add 或方法名 add/create 前缀 → CREATE；/delete → DELETE；
     * /update、/import/update → UPDATE；兜底 UPDATE（宿主守卫测试保证关键端点显式声明）。
     */
    static String deriveAction(String path, String methodName) {
        String p = path == null ? "" : path.toLowerCase();
        String m = methodName == null ? "" : methodName.toLowerCase();
        if (p.contains("/insert") || p.contains("/import/add") || m.startsWith("add") || m.startsWith("create")) {
            return "CREATE";
        }
        if (p.contains("/delete")) {
            return "DELETE";
        }
        if (p.contains("/import/update") || p.contains("/update")) {
            return "UPDATE";
        }
        return "UPDATE";
    }

    // ==================== id 三级提取 ====================

    /**
     * 资源标识三级提取：① {@code idParam} 同名绑定入参；② 路径模板变量 {@code {idParam}}；
     * ③ 兜底反射第一个入参对象的 {@code getId()}。取不到返回 null，绝不抛。
     */
    static String resolveId(String idParam, Map<String, String> namedArgs,
                            Map<String, String> pathVars, Object[] args) {
        if (idParam != null) {
            if (namedArgs != null && namedArgs.containsKey(idParam)) {
                String v = namedArgs.get(idParam);
                if (v != null && !v.isEmpty()) {
                    return v;
                }
            }
            if (pathVars != null && pathVars.containsKey(idParam)) {
                String v = pathVars.get(idParam);
                if (v != null && !v.isEmpty()) {
                    return v;
                }
            }
        }
        return firstIdGetter(args);
    }

    /**
     * 显式注解切面的 id 提取（<b>非反射</b>）：仅①{@code idParam} 同名绑定入参 ②路径模板变量。
     * 显式模式不做 {@code getId()} 反射（review P1-2：恢复"省略 bizId 即 null"的可预测行为）；
     * 但方法级注解端点跑在 web 线程上，请求面两腿必须保留——否则 {@code resource_id} 恒 NULL
     * （review P0-2：活动流水折叠/隐藏/材料文件名回填全依赖该列）。
     */
    static String resolveIdFromWeb(String idParam, Map<String, String> namedArgs,
                                   Map<String, String> pathVars) {
        if (idParam == null || idParam.isEmpty()) {
            return null;
        }
        if (namedArgs != null && namedArgs.containsKey(idParam)) {
            String v = namedArgs.get(idParam);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        if (pathVars != null && pathVars.containsKey(idParam)) {
            String v = pathVars.get(idParam);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    /** 反射第一个非空 {@code getId()}（update/delete 的 body VO/IdReqVO 命中；insert 前 id 为空返回 null） */
    static String firstIdGetter(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg == null || arg instanceof String || arg.getClass().isEnum()
                    || arg instanceof Number || arg instanceof Boolean || arg instanceof Map) {
                continue;
            }
            try {
                Method getId = arg.getClass().getMethod("getId");
                Object id = getId.invoke(arg);
                if (id != null) {
                    return String.valueOf(id);
                }
            } catch (NoSuchMethodException ignore) {
                // 该入参无 getId()，继续看下一个
            } catch (Exception e) {
                // 反射调用异常：跳过该入参，不因审计报错影响判定
            }
        }
        return null;
    }

    // ==================== 绑定入参/路径变量 ====================

    /** 把 @RequestParam/@PathVariable 简单入参按"声明名→值"装表（不反射转文本对象，规避泄密） */
    static Map<String, String> bindNamedArgs(Method method, Object[] args) {
        Map<String, String> out = new HashMap<>();
        Annotation[][] paramAnnos = method.getParameterAnnotations();
        Class<?>[] paramTypes = method.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            String name = declaredParamName(paramAnnos[i]);
            if (name == null || i >= args.length) {
                continue;
            }
            Object value = args[i];
            if (value != null && !(value instanceof String) && !(value instanceof Number)
                    && !(value instanceof Boolean) && !value.getClass().isEnum()) {
                continue; // 复杂对象不做 id 源（其 id 走 firstIdGetter 兜底）
            }
            out.put(name, value == null ? null : String.valueOf(value));
        }
        return out;
    }

    /** 参数声明名：@PathVariable/@RequestParam 的显式名（不依赖 -parameters 编译开关）；无注解名返回 null */
    static String declaredParamName(Annotation[] annotations) {
        for (Annotation anno : annotations) {
            if (anno instanceof org.springframework.web.bind.annotation.PathVariable) {
                org.springframework.web.bind.annotation.PathVariable pv =
                        (org.springframework.web.bind.annotation.PathVariable) anno;
                return firstNonEmpty(pv.value(), pv.name());
            }
            if (anno instanceof org.springframework.web.bind.annotation.RequestParam) {
                org.springframework.web.bind.annotation.RequestParam rp =
                        (org.springframework.web.bind.annotation.RequestParam) anno;
                return firstNonEmpty(rp.value(), rp.name());
            }
        }
        return null;
    }

    /**
     * 从 best-match 路径模板 + 实际请求 URI 对齐抽取 {@code {var}→值}（注解名缺省时的第二道来源）。
     */
    static Map<String, String> extractPathVars(HttpServletRequest request) {
        Map<String, String> out = new HashMap<>();
        Object patternAttr = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!(patternAttr instanceof String)) {
            return out;
        }
        String pattern = (String) patternAttr;
        String uri = requestPath(request);
        String[] pSeg = pattern.split("/");
        String[] uSeg = uri.split("/");
        if (pSeg.length != uSeg.length) {
            return out;
        }
        for (int i = 0; i < pSeg.length; i++) {
            String seg = pSeg[i];
            if (seg.length() > 2 && seg.charAt(0) == '{' && seg.charAt(seg.length() - 1) == '}') {
                String name = seg.substring(1, seg.length() - 1);
                // 支持 {fileId:.*} 之类正则约束：取冒号前名
                int colon = name.indexOf(':');
                if (colon > 0) {
                    name = name.substring(0, colon);
                }
                out.put(name, uSeg[i]);
            }
        }
        return out;
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        return b != null && !b.isEmpty() ? b : null;
    }
}
