package com.bidr.authorization.audit;

import org.aopalliance.aop.Advice;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.HandlerMapping;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: AuditTrailAutoAdvisorTest
 * Description: 默认拦截模式离线单测（不起 Spring 容器，ProxyFactory 真 Advisor 代理＋
 * JDK Proxy stub HttpServletRequest）。
 * <p>断言面：
 * P 组切点表达式拼装（两腿并集／全空 fail-fast）、
 * J 组判定矩阵（写动词／读白名单／防双记／豁免／框架继承面闸）、
 * D 组推导与提取（deriveAction／resolveId 三级）、
 * F 组全链（类级映射面／无注解裸端点／豁免／成功通道落行后 rethrow／group 兜底）。</p>
 * <p>守卫红线：切点表达式形态＝{@code within}/{@code execution} 两腿静态匹配——
 * 勿改 {@code @target}/{@code @within}（全应用代理事故，见 inkhub 2b 棒 VerifyError 为证）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author sharp (audit 框架增强：默认拦截模式 2026-10-24)
 */
public class AuditTrailAutoAdvisorTest {

    private final List<Object> published = new ArrayList<>();
    private final List<AuditTrailSink> sinks = new ArrayList<>();

    /** 宿主"以异常作成功通道"的模拟（如 Resp.notice 族） */
    public static class SuccessSignal extends RuntimeException {
        public SuccessSignal(String message) {
            super(message);
        }
    }

    /** 类级映射面控制器（声明类在本测试包＝业务包腿命中） */
    @AuditTrail(bizType = "kb_file", visibility = "COLLAB", idParam = "fileId")
    public static class PortalController {

        public String updateFile(@RequestParam("fileId") Long fileId) {
            return "ok";
        }

        public String insertFile(String name) {
            return "ok";
        }

        @AuditTrailIgnore
        public String ping() {
            return "pong";
        }

        public String noticeUpdate(@RequestParam("spaceId") Long spaceId) {
            throw new SuccessSignal("code 0");
        }

        @AuditTrail(bizType = "kb_file", action = "REPARSE", bizId = "#fileId")
        public String reparse(@RequestParam("fileId") Long fileId) {
            return "ok";
        }
    }

    /** 无任何注解的裸控制器（默认拦截哲学：漏注解也有行，bizType null 由 sink 定夺） */
    public static class BareController {

        public String updateThing(String name) {
            return "ok";
        }
    }

    // ---------------------------------------------------------------- harness

    @Before
    public void setUp() {
        published.clear();
        sinks.clear();
        RequestContextHolder.resetRequestAttributes();
        AuditContext.clear();
    }

    @After
    public void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        AuditContext.clear();
    }

    private static AuditTrailProperties.Auto props() {
        AuditTrailProperties.Auto p = new AuditTrailProperties.Auto();
        p.setControllerPackages(Arrays.asList("com.bidr.authorization.audit"));
        return p;
    }

    private AuditTrailAutoAdvisor advisor(AuditTrailProperties.Auto props,
                                          AuditTrailSuccessPolicy... policies) {
        return new AuditTrailAutoAdvisor(props,
                new AuditTrailWriter(provider(), publisher()),
                providerOf(Arrays.asList(policies)), emptyProvider());
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider() {
        List<AuditTrailSink> snapshot = sinks;
        return (ObjectProvider<T>) new ObjectProvider<AuditTrailSink>() {
            @Override
            public AuditTrailSink getObject() {
                if (snapshot.isEmpty()) {
                    throw new IllegalStateException("no sink");
                }
                return snapshot.get(0);
            }

            @Override
            public AuditTrailSink getObject(Object... args) {
                return getObject();
            }

            @Override
            public AuditTrailSink getIfAvailable() {
                return snapshot.isEmpty() ? null : snapshot.get(0);
            }

            @Override
            public AuditTrailSink getIfUnique() {
                return snapshot.size() == 1 ? snapshot.get(0) : null;
            }

            @Override
            public Stream<AuditTrailSink> orderedStream() {
                return snapshot.stream();
            }
        };
    }

    private static <T> ObjectProvider<T> providerOf(List<T> list) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() {
                if (list.isEmpty()) {
                    throw new IllegalStateException("no bean");
                }
                return list.get(0);
            }

            @Override
            public T getObject(Object... args) {
                return getObject();
            }

            @Override
            public T getIfAvailable() {
                return list.isEmpty() ? null : list.get(0);
            }

            @Override
            public T getIfUnique() {
                return list.size() == 1 ? list.get(0) : null;
            }

            @Override
            public Stream<T> orderedStream() {
                return list.stream();
            }
        };
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return providerOf(new ArrayList<>());
    }

    private ApplicationEventPublisher publisher() {
        return published::add;
    }

    /** JDK Proxy stub request（只应答审计链路会问到的方法；pattern 用于路径变量提取） */
    private static HttpServletRequest stubRequest(String method, String path, String pattern) {
        Map<String, Object> attrs = new HashMap<>();
        if (pattern != null) {
            attrs.put(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, pattern);
        }
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "getMethod":
                            return method;
                        case "getPathInfo":
                            return path;
                        case "getServletPath":
                            return null;
                        case "getRequestURI":
                            return path;
                        case "getAttribute":
                            return attrs.get(args[0]);
                        case "setAttribute":
                            attrs.put((String) args[0], args[1]);
                            return null;
                        default:
                            return null;
                    }
                });
    }

    private static void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private RecordingSink installSink() {
        RecordingSink sink = new RecordingSink();
        sinks.add(sink);
        return sink;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, AuditTrailProperties.Auto props, AuditTrailSuccessPolicy... policies) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvisor(advisor(props, policies));
        return (T) factory.getProxy();
    }

    public static class RecordingSink implements AuditTrailSink {
        final List<AuditRecord> written = new ArrayList<>();

        @Override
        public void write(AuditRecord record) {
            written.add(record);
        }
    }

    // ---------------------------------------------------------------- P 组：切点表达式拼装

    @Test
    public void p1_expressionBothLegsJoined() {
        AuditTrailProperties.Auto p = props();
        p.setFrameworkBaseClasses(Arrays.asList("com.bidr.kernel.controller.BaseAdminController"));
        assertEquals("within(com.bidr.authorization.audit..*) || "
                        + "execution(* com.bidr.kernel.controller.BaseAdminController+.*(..))",
                AuditTrailAutoAdvisor.pointcutExpression(p));
    }

    @Test
    public void p2_expressionEmptyWhenNoLegs() {
        assertEquals("", AuditTrailAutoAdvisor.pointcutExpression(new AuditTrailProperties.Auto()));
    }

    @Test
    public void p3_blankEntriesSkipped() {
        AuditTrailProperties.Auto p = props();
        p.setControllerPackages(Arrays.asList("com.x.controller", " ", null));
        assertEquals("within(com.x.controller..*)", AuditTrailAutoAdvisor.pointcutExpression(p));
    }

    @Test
    public void p4_failFastWhenEnabledButNoLegs() {
        try {
            new AuditTrailAutoAdvisor(new AuditTrailProperties.Auto(),
                    new AuditTrailWriter(provider(), publisher()), emptyProvider(), emptyProvider());
            fail("开关开了但两腿全空应启动即失败（静默空转比报错更难排查）");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("controllerPackages"));
        }
    }

    // ---------------------------------------------------------------- J 组：判定矩阵（静态）

    private static Method method(String name, Class<?>... params) throws Exception {
        return PortalController.class.getMethod(name, params);
    }

    @Test
    public void j1_writeVerbGate() throws Exception {
        assertTrue(AuditTrailAutoAdvisor.shouldRecord("POST", "/web/kb/file/update",
                method("updateFile", Long.class), PortalController.class, props()));
        assertFalse("GET 一律放行", AuditTrailAutoAdvisor.shouldRecord("GET", "/web/kb/file/update",
                method("updateFile", Long.class), PortalController.class, props()));
        assertFalse("写动词集外（TRACE）放行", AuditTrailAutoAdvisor.shouldRecord("TRACE", "/web/kb/file/update",
                method("updateFile", Long.class), PortalController.class, props()));
    }

    @Test
    public void j2_readPathWhitelistGate() throws Exception {
        assertFalse("框架读尾段放行", AuditTrailAutoAdvisor.shouldRecord("POST", "/web/kb/file/general/list",
                method("updateFile", Long.class), PortalController.class, props()));
        assertFalse("导入进度读尾段放行", AuditTrailAutoAdvisor.shouldRecord("POST", "/import/add/progress",
                method("updateFile", Long.class), PortalController.class, props()));
    }

    @Test
    public void j3_methodLevelAnnotationSkipped_antiDoubleWrite() throws Exception {
        assertFalse("方法级 @AuditTrail 端点跳过默认拦截（显式切面全权负责）",
                AuditTrailAutoAdvisor.shouldRecord("POST", "/web/kb/file/reparse",
                        method("reparse", Long.class), PortalController.class, props()));
    }

    @Test
    public void j4_ignoreGate() throws Exception {
        assertFalse("方法级 @AuditTrailIgnore 豁免", AuditTrailAutoAdvisor.shouldRecord("POST", "/web/kb/ping",
                method("ping"), PortalController.class, props()));
        assertFalse("类级 @AuditTrailIgnore 豁免（方法压类的反例：类级生效）",
                AuditTrailAutoAdvisor.shouldRecord("POST", "/web/kb/ping", method("ping"),
                        IgnoredController.class, props()));
    }

    @AuditTrailIgnore
    public static class IgnoredController {

        public String anything() {
            return "ok";
        }
    }

    @Test
    public void j5_frameworkInheritedFaceGate() throws Exception {
        Method declaredElsewhere = Object.class.getMethod("toString");
        // 声明类不在业务包（框架基类声明形态）＋目标类无类级 @AuditTrail → 不记
        assertFalse("框架继承面无类级注解＝框架会话面不记",
                AuditTrailAutoAdvisor.isBusinessDeclaredOrClassAnnotated(
                        declaredElsewhere, BareController.class, props().getControllerPackages()));
        // 声明类不在业务包＋目标类带类级 @AuditTrail → 放行（Portal 继承写面形态）
        assertTrue("框架继承面有类级注解＝业务写面放行",
                AuditTrailAutoAdvisor.isBusinessDeclaredOrClassAnnotated(
                        declaredElsewhere, PortalController.class, props().getControllerPackages()));
        // 声明类在业务包内 → 直接放行（业务声明方法）
        assertTrue("业务包内声明方法直接放行",
                AuditTrailAutoAdvisor.isBusinessDeclaredOrClassAnnotated(
                        method("updateFile", Long.class), PortalController.class, props().getControllerPackages()));
        // 类级注解合并可见性（AnnotatedElementUtils 口径）
        assertTrue(AnnotatedElementUtils.findMergedAnnotation(PortalController.class, AuditTrail.class) != null);
    }

    // ---------------------------------------------------------------- D 组：推导与提取（静态）

    @Test
    public void d1_deriveActionMatrix() {
        assertEquals("CREATE", AuditWebSupport.deriveAction("/web/kb/file/insert", "save"));
        assertEquals("CREATE", AuditWebSupport.deriveAction("/web/kb/import/add/progress", "x"));
        assertEquals("CREATE", AuditWebSupport.deriveAction("/x", "addFile"));
        assertEquals("CREATE", AuditWebSupport.deriveAction("/x", "createDoc"));
        assertEquals("DELETE", AuditWebSupport.deriveAction("/web/kb/file/delete", "remove"));
        assertEquals("UPDATE", AuditWebSupport.deriveAction("/web/kb/file/update", "save"));
        assertEquals("UPDATE", AuditWebSupport.deriveAction("/import/update/do", "x"));
        assertEquals("UPDATE", AuditWebSupport.deriveAction("/web/kb/other", "save"));
    }

    @Test
    public void d2_resolveIdThreeLegs() {
        Map<String, String> named = new HashMap<>();
        named.put("id", "7");
        Map<String, String> vars = new HashMap<>();
        vars.put("id", "8");
        assertEquals("同名绑定入参优先", "7",
                AuditWebSupport.resolveId("id", named, vars, new Object[0]));
        assertEquals("路径模板变量次之", "8",
                AuditWebSupport.resolveId("id", new HashMap<>(), vars, new Object[0]));
        Object withId = new Object() {
            @SuppressWarnings("unused")
            public Long getId() {
                return 9L;
            }
        };
        assertEquals("getId 反射兜底", "9",
                AuditWebSupport.resolveId("id", new HashMap<>(), new HashMap<>(), new Object[]{withId}));
        assertNull("三级全空为 null（insert 前 id 未生成为合法形态）",
                AuditWebSupport.resolveId("id", new HashMap<>(), new HashMap<>(), new Object[]{"x"}));
    }

    // ---------------------------------------------------------------- F 组：全链（真代理＋stub request）

    @Test
    public void f1_classAnnotatedEndpoint_fullRecord() {
        RecordingSink sink = installSink();
        PortalController target = proxy(new PortalController(), props());
        bind(stubRequest("POST", "/web/kb/file/9", "/web/kb/file/{fileId}"));
        assertEquals("ok", target.updateFile(9L));
        assertEquals(1, sink.written.size());
        AuditRecord r = sink.written.get(0);
        assertEquals("类级映射面供 bizType", "kb_file", r.getBizType());
        assertEquals("action 留空按方法名/路径推导", "UPDATE", r.getAction());
        assertEquals("id 三级提取（@RequestParam 绑定腿）", "9", r.getBizId());
        assertEquals("类级 visibility 透传", "COLLAB", r.getVisibility());
        assertEquals("endpoint 溯源", "POST /web/kb/file/9", r.getEndpoint());
    }

    @Test
    public void f2_insertDerivesCreate_noIdLegNull() {
        RecordingSink sink = installSink();
        PortalController target = proxy(new PortalController(), props());
        bind(stubRequest("POST", "/web/kb/file/insert", "/web/kb/file/insert"));
        target.insertFile("a.pdf");
        AuditRecord r = sink.written.get(0);
        assertEquals("CREATE", r.getAction());
        assertNull("insert 前 id 未生成＝null 合法（宿主 Service 补 resourceIdOverride）", r.getBizId());
    }

    @Test
    public void f3_ignoredEndpoint_noRow() {
        RecordingSink sink = installSink();
        PortalController target = proxy(new PortalController(), props());
        bind(stubRequest("POST", "/web/kb/ping", "/web/kb/ping"));
        assertEquals("pong", target.ping());
        assertEquals("豁免端点不产行", 0, sink.written.size());
    }

    @Test
    public void f4_methodAnnotatedEndpoint_notDoubleWritten() {
        RecordingSink sink = installSink();
        PortalController target = proxy(new PortalController(), props());
        bind(stubRequest("POST", "/web/kb/file/reparse", "/web/kb/file/reparse"));
        // 显式注解切面不在本测试装配——默认拦截跳过后即无行（防双记语义）
        target.reparse(5L);
        assertEquals("方法级注解端点默认拦截不产行（交显式切面）", 0, sink.written.size());
    }

    @Test
    public void f5_bareEndpoint_rowWithNullBizType() {
        RecordingSink sink = installSink();
        BareController target = proxy(new BareController(), props());
        bind(stubRequest("POST", "/web/kb/thing/update", "/web/kb/thing/update"));
        target.updateThing("t");
        assertEquals("无注解端点也产行（默认拦截哲学）", 1, sink.written.size());
        assertNull("bizType null 交 sink 定夺", sink.written.get(0).getBizType());
        assertEquals("UPDATE", sink.written.get(0).getAction());
    }

    @Test
    public void f6_successChannelPolicy_rowWritten_thenRethrown() {
        RecordingSink sink = installSink();
        AuditTrailSuccessPolicy policy = thrown -> thrown instanceof SuccessSignal;
        PortalController target = proxy(new PortalController(), props(), policy);
        bind(stubRequest("POST", "/web/kb/space/update", "/web/kb/space/update"));
        try {
            target.noticeUpdate(5L);
            fail("成功通道异常必须原样 rethrow");
        } catch (SuccessSignal expected) {
            assertEquals("code 0", expected.getMessage());
        }
        assertEquals("成功通道照常产审计行", 1, sink.written.size());
        AuditRecord r = sink.written.get(0);
        assertEquals("group 兜底腿：绑定入参按 groupParam（缺省 spaceId）取", "5", r.getGroupKey());
        assertEquals("kb_file", r.getBizType());
    }

    @Test
    public void f7_getRequest_notRecorded() {
        RecordingSink sink = installSink();
        PortalController target = proxy(new PortalController(), props());
        bind(stubRequest("GET", "/web/kb/file/9", "/web/kb/file/{fileId}"));
        target.updateFile(9L);
        assertEquals("GET 不记", 0, sink.written.size());
    }
}
