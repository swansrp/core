package com.bidr.authorization.audit;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.event.ServiceExceptionEvent;
import com.bidr.kernel.exception.ServiceException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: AuditTrailAspectTest
 * Description: @AuditTrail 切面全链离线单测（不起 Spring 容器，AspectJProxyFactory 真代理）。
 * <p>断言面：
 * S 组 SpEL 三腿（M-6 钉：#result／#参数名／reason 模板插值，附 #args／#ctx 腿）、
 * W 组写入链（字段列值对齐＋operator/tenant 空安全）、
 * E 组异常吞并（裁定 2：sink 缺失/sink 抛错/SpEL 失败——业务返回值不受影响＋ERROR 级告警事件）、
 * C 组清理（AuditContext 方法级收尾，异常路径同样清）。</p>
 * <p>告警接线断言：切面 publish {@link ServiceExceptionEvent}（ERROR 级、消息含 bizType 定位信息）
 * ——ServiceExceptionEmailListener 的 @Async @EventListener 消费面为框架既有保证，不在本测。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 */
public class AuditTrailAspectTest {

    private final List<Object> published = new ArrayList<>();
    private final List<AuditTrailSink> sinks = new ArrayList<>();

    private AuditTrailAspect aspect;
    private BizService biz;

    @Before
    public void setUp() {
        TenantContext.remove();
        AccountContext.remove();
        published.clear();
        sinks.clear();
        aspect = new AuditTrailAspect(provider(), publisher(), emptyProvider(), emptyProvider());
        biz = proxy(new BizService(), aspect);
    }

    @After
    public void tearDown() {
        TenantContext.remove();
        AccountContext.remove();
        AuditContext.clear();
    }

    // ---------------------------------------------------------------- harness

    private ApplicationEventPublisher publisher() {
        return published::add;
    }

    /** 可变内容 provider（成功策略/分组解析器注入用） */
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

    /** 空 provider（默认无策略/无解析器形态——宿主零注册时的行为基线） */
    private static <T> ObjectProvider<T> emptyProvider() {
        return providerOf(new ArrayList<>());
    }

    private BizService proxy(BizService target) {
        return proxy(target, aspect);
    }

    private BizService proxy(BizService target, AuditTrailAspect aspectToWire) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspectToWire);
        return factory.getProxy();
    }

    private ObjectProvider<AuditTrailSink> provider() {
        List<AuditTrailSink> snapshot = sinks;
        return new ObjectProvider<AuditTrailSink>() {
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

    private RecordingSink installSink() {
        RecordingSink sink = new RecordingSink();
        sinks.add(sink);
        return sink;
    }

    private static void assertAuditAlert(List<Object> events, String expectBizTypeInMessage) {
        assertEquals("应发布 1 条审计告警事件", 1, events.size());
        Object event = events.get(0);
        assertTrue("告警事件应为 ServiceExceptionEvent（运维通知链正源）", event instanceof ServiceExceptionEvent);
        ServiceExceptionEvent sev = (ServiceExceptionEvent) event;
        assertEquals("审计失败必须走 ERROR 级（过默认 minLevel 阈值）", sev.getErrLevel().name(), "ERROR");
        assertTrue("告警消息应含 bizType 定位信息",
                sev.getErrorMessage() != null && sev.getErrorMessage().contains(expectBizTypeInMessage));
    }

    // ---------------------------------------------------------------- 目标 bean

    public static class Doc {
        private final String docNo;

        public Doc(String docNo) {
            this.docNo = docNo;
        }

        public String getDocNo() {
            return docNo;
        }
    }

    public static class BizService {

        /** 主腿：#参数名 + #result 模板 + ctx 约定键（old/new 列）。 */
        @AuditTrail(bizType = "merchant_profile", action = "change", field = "grade",
                bizId = "#profileId",
                reason = "调整 #{#profileId} 等级为 #{#result}",
                operatorIdentity = "staff")
        public String changeGrade(Long profileId) {
            AuditContext.put(AuditContext.OLD_VALUE, "silver");
            AuditContext.put(AuditContext.NEW_VALUE, "gold");
            return "gold";
        }

        /** #args 按位腿。 */
        @AuditTrail(bizType = "doc", action = "create", bizId = "#args[0]")
        public String argsLeg(String docNo) {
            return docNo;
        }

        /** #result 对象属性腿。 */
        @AuditTrail(bizType = "doc", action = "create", bizId = "#result.docNo")
        public Doc resultLeg() {
            return new Doc("D-1");
        }

        /** #ctx 直引腿（非约定键）。 */
        @AuditTrail(bizType = "doc", action = "create", bizId = "#id",
                reason = "单 #{#id}：旧 #{#ctx.get('oldValue')} 新 #{#ctx.get('newValue')}")
        public String ctxLeg(Long id) {
            AuditContext.put("oldValue", "v0");
            AuditContext.put("newValue", "v1");
            return "done";
        }

        /** 纯字面量腿（reason 无表达式、field/operatorIdentity 缺省——空串归一 null）。 */
        @AuditTrail(bizType = "doc", action = "create")
        public String literalLeg() {
            return "plain";
        }

        /** SpEL 求值失败腿（未知变量——组装失败，审计行丢弃但业务不受影响）。 */
        @AuditTrail(bizType = "doc", action = "create", bizId = "#nope + 1")
        public String brokenSpel() {
            return "biz-ok";
        }

        /** 业务异常腿：不产审计行、异常原样上抛、上下文仍清。 */
        @AuditTrail(bizType = "doc", action = "create", bizId = "#id")
        public String throwsBiz(Long id) {
            AuditContext.put("k", "v");
            throw new ServiceException("boom");
        }

        /** 空参方法（参数名发现为空数组的边界）。 */
        @AuditTrail(bizType = "doc", action = "create")
        public String noArg() {
            return "no-arg-ok";
        }

        /** 活动流水腿：group SpEL＋label/detail 模板＋visibility 字面量。 */
        @AuditTrail(bizType = "kb_file", action = "UPLOAD", bizId = "#fileId",
                group = "#spaceId", resourceLabel = "#{#fileName}", detail = "上传解析完成",
                visibility = "COLLAB")
        public String uploadLeg(Long spaceId, Long fileId, String fileName) {
            return "ok";
        }

        /** 成功通道腿：方法体抛成功信号异常（宿主 policy 计成功——落行后原样 rethrow）。 */
        @AuditTrail(bizType = "kb_space", action = "CREATE", bizId = "#seed")
        public String noticeLeg(Long seed) {
            throw new SuccessChannelException("code 0 payload");
        }

        /** 覆写腿：注解给一套、方法体 ctx 约定键覆写另一套（覆写必须全胜）。 */
        @AuditTrail(bizType = "kb_member", action = "GRANT", bizId = "#groupId")
        public String grantLeg(Long groupId) {
            AuditContext.actionOverride("REVOKE");
            AuditContext.resourceIdOverride("G-9");
            AuditContext.label("外部组");
            AuditContext.detail("改为撤销");
            AuditContext.groupOverride("77");
            return "ok";
        }

        /** 装饰字段烂表达式腿：group 求值失败＝该字段置 null、行照发（分级纪律）。 */
        @AuditTrail(bizType = "kb_page", action = "UPDATE", bizId = "#id", group = "#broken +")
        public String brokenGroupLeg(Long id) {
            return "ok";
        }

        /** resolver 兜底腿：group 属性空＋无覆写→宿主 resolver 供值。 */
        @AuditTrail(bizType = "kb_skill", action = "BIND", bizId = "#skillId")
        public String resolverLeg(Long skillId) {
            return "ok";
        }
    }

    /** 宿主"以异常作成功通道"的模拟（如 Resp.notice 族 NoticeException） */
    public static class SuccessChannelException extends RuntimeException {
        public SuccessChannelException(String message) {
            super(message);
        }
    }

    public static class RecordingSink implements AuditTrailSink {
        final List<AuditRecord> written = new ArrayList<>();
        boolean fail = false;

        @Override
        public void write(AuditRecord record) {
            if (fail) {
                throw new IllegalStateException("sink down");
            }
            written.add(record);
        }
    }

    // ---------------------------------------------------------------- S 组：SpEL 三腿（M-6 钉）

    @Test
    public void s1_resultInTemplate_and_paramName() {
        RecordingSink sink = installSink();
        assertEquals("gold", biz.changeGrade(42L));
        assertEquals(1, sink.written.size());
        AuditRecord r = sink.written.get(0);
        assertEquals("#参数名求值（#profileId）", "42", r.getBizId());
        assertEquals("reason 模板插值（#{#profileId}＋#{#result}）", "调整 42 等级为 gold", r.getReason());
    }

    @Test
    public void s2_argsPositionalLeg() {
        RecordingSink sink = installSink();
        biz.argsLeg("DOC-9");
        assertEquals("D OC-9 按位取参（#args[0]）应为 DOC-9", "DOC-9", sink.written.get(0).getBizId());
    }

    @Test
    public void s3_resultPropertyLeg() {
        RecordingSink sink = installSink();
        biz.resultLeg();
        assertEquals("#result 对象属性求值（#result.docNo）", "D-1", sink.written.get(0).getBizId());
    }

    @Test
    public void s4_ctxVariableLeg_and_oldNewConvention() {
        RecordingSink sink = installSink();
        biz.ctxLeg(7L);
        AuditRecord r = sink.written.get(0);
        assertEquals("#ctx.get('oldValue') 模板插值", "单 7：旧 v0 新 v1", r.getReason());
        assertEquals("约定键 oldValue 自动并入审计行", "v0", r.getOldValue());
        assertEquals("约定键 newValue 自动并入审计行", "v1", r.getNewValue());
    }

    // ---------------------------------------------------------------- W 组：写入链列值

    @Test
    public void w1_columnAlignedFields_withOperatorAndTenant() {
        RecordingSink sink = installSink();
        TenantContext.setCurrentTenantId(7L);
        AccountInfo account = new AccountInfo();
        account.setCustomerNumber("op-1");
        AccountContext.set(account);

        biz.changeGrade(42L);

        AuditRecord r = sink.written.get(0);
        assertEquals("merchant_profile", r.getBizType());
        assertEquals("change", r.getAction());
        assertEquals("grade", r.getField());
        assertEquals("42", r.getBizId());
        assertEquals("调整 42 等级为 gold", r.getReason());
        assertEquals("操作人取 AccountContext.getOperator()", "op-1", r.getOperatorUserId());
        assertEquals("staff", r.getOperatorIdentity());
        assertEquals("tenantId 取 TenantContext.currentTenantId()", Long.valueOf(7L), r.getTenantId());
        assertNotNull("occurredAt 服务端组装时刻", r.getOccurredAt());
    }

    @Test
    public void w2_operatorAndTenantNullSafe_andEmptyNormalized() {
        RecordingSink sink = installSink();
        biz.literalLeg();
        AuditRecord r = sink.written.get(0);
        assertTrue("无登录档时 operatorUserId 空安全（EMPTY 串）",
                r.getOperatorUserId() == null || r.getOperatorUserId().isEmpty());
        assertNull("未选租户时 tenantId 为 null（平台级动作形态）", r.getTenantId());
        assertNull("field 缺省空串归一 null", r.getField());
        assertNull("reason 缺省空串归一 null", r.getReason());
        assertNull("operatorIdentity 缺省空串归一 null", r.getOperatorIdentity());
    }

    // ---------------------------------------------------------------- E 组：异常吞并（裁定 2）

    @Test
    public void e1_missingSink_skipWithErrorAlert_businessUnaffected() {
        installSink();
        sinks.clear(); // 模拟宿主未装 sink
        assertEquals("无 sink 业务照常返回", "gold", biz.changeGrade(1L));
        assertAuditAlert(published, "merchant_profile");
    }

    @Test
    public void e2_sinkThrows_businessReturnValueIntact_andAlert() {
        RecordingSink bad = installSink();
        bad.fail = true;
        assertEquals("sink 抛错不改变业务返回值", "gold", biz.changeGrade(42L));
        assertEquals("失败的 sink 不产审计行", 0, bad.written.size());
        assertAuditAlert(published, "merchant_profile");
    }

    @Test
    public void e3_multiSink_oneFails_othersStillWrite_singleAlert() {
        RecordingSink bad = installSink();
        bad.fail = true;
        RecordingSink good = installSink();
        assertEquals("gold", biz.changeGrade(42L));
        assertEquals("失败 sink 不产行", 0, bad.written.size());
        assertEquals("健康 sink 照常收行", 1, good.written.size());
        assertAuditAlert(published, "merchant_profile");
    }

    @Test
    public void e4_brokenSpel_rowDropped_businessUnaffected_andAlert() {
        installSink();
        assertEquals("SpEL 求值失败不影响业务", "biz-ok", biz.brokenSpel());
        assertAuditAlert(published, "doc");
    }

    @Test
    public void e5_businessThrows_noRow_noAuditAlert_exceptionPropagates() {
        installSink();
        try {
            biz.throwsBiz(1L);
            fail("业务异常必须原样上抛");
        } catch (ServiceException expected) {
            assertEquals("boom", expected.getMessage());
        }
        assertTrue("业务异常不产审计行", ((RecordingSink) sinks.get(0)).written.isEmpty());
        assertEquals("业务异常不是审计失败——审计侧不告警（全局异常处理器职责）", 0, published.size());
        assertNull("异常路径上下文仍收尾（防泄漏）", AuditContext.get("k"));
    }

    // ---------------------------------------------------------------- C 组：清理

    @Test
    public void c1_contextClearedAfterAuditedMethod() {
        installSink();
        biz.changeGrade(1L);
        assertNull("方法体内 put 的运行时上下文在切面收尾后清空", AuditContext.get(AuditContext.NEW_VALUE));
        assertTrue("上下文快照为空", AuditContext.snapshot().isEmpty());
    }

    @Test
    public void c2_contextClearedOnBrokenSpelPath() {
        installSink();
        AuditContext.put("leftover", "x"); // 前置残留（模拟方法外污染）
        biz.brokenSpel();
        assertNull("组装失败路径也必须收尾", AuditContext.get("leftover"));
    }

    @Test
    public void c3_noArgMethodStillAudited() {
        RecordingSink sink = installSink();
        assertEquals("no-arg-ok", biz.noArg());
        assertEquals(1, sink.written.size());
        assertEquals("doc", sink.written.get(0).getBizType());
        assertFalse(sink.written.get(0).getOccurredAt() == null);
    }

    @Test
    public void c4_occurredAtIsServerTime() {
        RecordingSink sink = installSink();
        Date before = new Date();
        biz.literalLeg();
        Date after = new Date();
        AuditRecord r = sink.written.get(0);
        assertFalse("occurredAt 不得早于调用窗口", r.getOccurredAt().before(before));
        assertFalse("occurredAt 不得晚于调用窗口", r.getOccurredAt().after(after));
    }

    // ---------------------------------------------------------------- X 组：框架增强（活动流水维度/成功策略/覆写）

    @Test
    public void x1_activityFlowColumns_groupLabelDetailVisibility() {
        RecordingSink sink = installSink();
        biz.uploadLeg(5L, 9L, "a.pdf");
        AuditRecord r = sink.written.get(0);
        assertEquals("group SpEL 求值", "5", r.getGroupKey());
        assertEquals("label 模板插值", "a.pdf", r.getResourceLabel());
        assertEquals("detail 模板（纯文本原样）", "上传解析完成", r.getDetail());
        assertEquals("visibility 字面量透传", "COLLAB", r.getVisibility());
        assertNull("非 web 测试线程无请求面，endpoint 为 null", r.getEndpoint());
    }

    @Test
    public void x2_ctxConventionKeysBeatAnnotation() {
        RecordingSink sink = installSink();
        biz.grantLeg(3L);
        AuditRecord r = sink.written.get(0);
        assertEquals("actionOverride 覆写注解 GRANT", "REVOKE", r.getAction());
        assertEquals("resourceIdOverride 覆写 bizId SpEL", "G-9", r.getBizId());
        assertEquals("label 覆写", "外部组", r.getResourceLabel());
        assertEquals("detail 覆写", "改为撤销", r.getDetail());
        assertEquals("groupOverride 覆写", "77", r.getGroupKey());
    }

    @Test
    public void x3_successPolicy_successChannelRowWritten_thenRethrown() {
        RecordingSink sink = installSink();
        AuditTrailSuccessPolicy policy = thrown -> thrown instanceof SuccessChannelException;
        AuditTrailAspect policyAspect =
                new AuditTrailAspect(provider(), publisher(), providerOf(Arrays.asList(policy)), emptyProvider());
        BizService biz2 = proxy(new BizService(), policyAspect);
        try {
            biz2.noticeLeg(11L);
            fail("成功通道异常必须原样 rethrow（MVC 异常处理器依赖）");
        } catch (SuccessChannelException expected) {
            assertEquals("code 0 payload", expected.getMessage());
        }
        assertEquals("成功通道照常产审计行", 1, sink.written.size());
        assertEquals("11", sink.written.get(0).getBizId());
        assertEquals("无审计失败告警", 0, published.size());
    }

    @Test
    public void x3b_noPolicy_exceptionIsFailure_noRow() {
        RecordingSink sink = installSink();
        try {
            biz.noticeLeg(11L);
            fail("异常必须上抛");
        } catch (SuccessChannelException expected) {
            // 默认（无策略）＝异常即失败
        }
        assertTrue("无策略时成功通道异常按失败处理不产行", sink.written.isEmpty());
        assertEquals("按业务异常口径：不告警", 0, published.size());
    }

    @Test
    public void x4_decorativeFieldSpelFailureRowSurvives() {
        RecordingSink sink = installSink();
        assertEquals("烂 group 表达式不影响业务返回", "ok", biz.brokenGroupLeg(6L));
        assertEquals("装饰字段失败行照发（分级纪律：只丢字段不丢行）", 1, sink.written.size());
        assertNull("group 求值失败置 null", sink.written.get(0).getGroupKey());
        assertEquals("bizId（定位关键）不受装饰字段失败牵连", "6", sink.written.get(0).getBizId());
    }

    @Test
    public void x5_groupResolverFallback() {
        RecordingSink sink = installSink();
        AuditTrailGroupResolver resolver = () -> "S-1";
        AuditTrailAspect resolverAspect = new AuditTrailAspect(
                provider(), publisher(), emptyProvider(), providerOf(Arrays.asList(resolver)));
        proxy(new BizService(), resolverAspect).resolverLeg(8L);
        assertEquals("group 属性空＋无覆写→resolver 供值", "S-1", sink.written.get(0).getGroupKey());
    }

    @Test
    public void x6_bizIdBlankFallsBackToIdGetterReflection() {
        // bizId 属性缺省（空）→ 反射兜底腿：第一个带 getId() 的入参对象（named/pathVar 腿由 auto 侧全链测试覆盖）
        Object withId = new Object() {
            @SuppressWarnings("unused")
            public Long getId() {
                return 66L;
            }
        };
        assertEquals("66", AuditWebSupport.firstIdGetter(new Object[]{withId}));
        assertNull("简单类型入参（无 getId）返回 null", AuditWebSupport.firstIdGetter(new Object[]{"plain", 42L}));
    }
}
