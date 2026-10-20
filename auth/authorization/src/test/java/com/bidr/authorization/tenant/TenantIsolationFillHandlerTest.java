package com.bidr.authorization.tenant;

import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.exception.ServiceException;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Title: TenantIsolationFillHandlerTest
 * Description: 租户写闸（实体面）离线单测——不起 Spring、不连库，直接用
 * {@link SystemMetaObject#forObject(Object)} 造 MP 在 insertFill/updateFill 收到的同种 MetaObject。
 * <p>补与拒都在本层，落库前提是实体把 {@code tenantId} 声明成 {@code fill}
 * （守卫见 {@link TenantLineIsolationTest} F 组，SQL 形态由真库 IT 钉）。</p>
 * <p>断言面：I 组 INSERT（补位/不覆盖/跨租户拒/未选租户拒/内部段与自由实体不介入）、
 * U 组 UPDATE（改归属拒/不改归属放行/无当前租户不误伤读）、A 组裸字段形态（实测事实留档）。
 * 异常族分界也在这套里钉：用户可触发的拒（i3/i4/u1/a2/a3）＝{@code ServiceException}
 * （前端只读它的 details），开发期契约（a4）＝{@code IllegalStateException}。</p>
 * <p>每档判据都配负向腿：只断"抛了"不算通过，须同时断"不该抛的场合确实没抛"——
 * 否则一个恒抛的实现能骗过全部正向例（先例：{@link TenantIsolatedConventionTest}）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TenantIsolationFillHandlerTest {

    private static final long CURRENT = 7L;
    private static final long FOREIGN = 99L;

    private final TenantIsolationFillHandler handler = new TenantIsolationFillHandler();

    @Before
    public void setUp() {
        TenantContext.remove();
    }

    @After
    public void tearDown() {
        TenantContext.remove();
    }

    private static ProbeIsolatedEntity isolatedEntity(Long tenantId) {
        ProbeIsolatedEntity entity = new ProbeIsolatedEntity();
        entity.setId(1L);
        entity.setName("n");
        entity.setTenantId(tenantId);
        return entity;
    }

    private void asTenantUser() {
        TenantContext.set(Collections.singletonList(CURRENT));
        TenantContext.setCurrentTenantId(CURRENT);
    }

    // ==================== I 组：INSERT ====================

    @Test
    public void i1_missingTenantId_isFilledWithCurrent() {
        asTenantUser();
        ProbeIsolatedEntity entity = isolatedEntity(null);
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals("漏填必须补成当前租户", Long.valueOf(CURRENT), entity.getTenantId());
    }

    @Test
    public void i2_explicitSameTenant_isNotOverwritten() {
        asTenantUser();
        ProbeIsolatedEntity entity = isolatedEntity(CURRENT);
        entity.setName("keep-me");
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals("业务显式值须原样保留（fillStrategy 空才填）", Long.valueOf(CURRENT), entity.getTenantId());
        assertEquals("不得碰其它字段", "keep-me", entity.getName());
    }

    @Test
    public void i3_explicitForeignTenant_rejectedWithWayOut() {
        asTenantUser();
        ProbeIsolatedEntity entity = isolatedEntity(FOREIGN);
        try {
            handler.insertFill(SystemMetaObject.forObject(entity));
            fail("声明租户≠当前租户必须拒（跨租户写入通道）");
        } catch (ServiceException expected) {
            String msg = expected.getMessage();
            assertTrue("消息须点明跨租户：" + msg, msg.contains("跨租户写入被拒"));
            assertTrue("消息须带上两个租户值便于定位：" + msg,
                    msg.contains(String.valueOf(FOREIGN)) && msg.contains(String.valueOf(CURRENT)));
            assertTrue("阻断提示须就地给出口：" + msg, msg.contains("runWithoutIsolation"));
        }
    }

    @Test
    public void i4_noCurrentTenant_rejectsEvenWithExplicitValue() {
        // 用户终裁：不指定租户＝不能建数据。系统管理级（租户集 null/空）与租户用户未选都落这里，
        // 且业务透传进来的 tenant_id 也不许放行——否则老 token 可写进任意租户面。
        ProbeIsolatedEntity entity = isolatedEntity(FOREIGN);
        try {
            handler.insertFill(SystemMetaObject.forObject(entity));
            fail("无当前租户时 INSERT 隔离表必须拒");
        } catch (ServiceException expected) {
            assertTrue("文案须给可行动出口：" + expected.getMessage(),
                    expected.getMessage().contains("未指定租户") && expected.getMessage().contains("切换"));
        }

        asTenantUser();
        TenantContext.setCurrentTenantId(null);
        assertRejected(new Runnable() {
            @Override
            public void run() {
                handler.insertFill(SystemMetaObject.forObject(isolatedEntity(FOREIGN)));
            }
        });
    }

    @Test
    public void i5_internalSection_isNotTouched() {
        asTenantUser();
        // 内部段＝种子/迁移/对账的合法通道：既不补位也不拦"跨租户"
        ProbeIsolatedEntity nullTenantEntity = isolatedEntity(null);
        ProbeIsolatedEntity foreignTenantEntity = isolatedEntity(FOREIGN);
        TenantContext.runWithoutIsolation(new Runnable() {
            @Override
            public void run() {
                handler.insertFill(SystemMetaObject.forObject(nullTenantEntity));
                handler.insertFill(SystemMetaObject.forObject(foreignTenantEntity));
            }
        });
        assertNull("内部段不得补位", nullTenantEntity.getTenantId());
        assertEquals("内部段不得拦跨租户值", Long.valueOf(FOREIGN), foreignTenantEntity.getTenantId());
        // 负向腿：退出内部段恢复拦截（漏填会走补位而非抛）
        ProbeIsolatedEntity afterSection = isolatedEntity(null);
        handler.insertFill(SystemMetaObject.forObject(afterSection));
        assertEquals(Long.valueOf(CURRENT), afterSection.getTenantId());
    }

    @Test
    public void i6_freeEntity_neverEntersTheGate() {
        // 无当前租户 + 空租户集的最坏上下文：非标记实体照样不该被拦
        ProbeFreeEntity free = new ProbeFreeEntity();
        free.setId(2L);
        handler.insertFill(SystemMetaObject.forObject(free));
        assertEquals(Long.valueOf(2L), free.getId());

        asTenantUser();
        ProbeIsolatedEntity entity = isolatedEntity(null);
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals(Long.valueOf(CURRENT), entity.getTenantId());
    }

    @Test
    public void i7_platformTier_withSelectedTenant_isAllowedToWrite() {
        // 2026-10-20 用户改口正源留档：写闸不看平台档——有当前租户就能建，没有就拒（平台档不例外）
        asTenantUser();
        TenantContext.markPlatform();
        ProbeIsolatedEntity entity = isolatedEntity(null);
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals("平台档已选定租户＝按当前租户建，与租户用户同权", Long.valueOf(CURRENT), entity.getTenantId());
    }

    // ==================== U 组：UPDATE ====================

    @Test
    public void u1_updateMovingRowToForeignTenant_rejected() {
        asTenantUser();
        ProbeIsolatedEntity entity = isolatedEntity(FOREIGN);
        try {
            handler.updateFill(SystemMetaObject.forObject(entity));
            fail("实体带外租户 id 会把行搬出租户面（拦截器只加 WHERE 不管 SET），必须拒");
        } catch (ServiceException expected) {
            assertTrue(expected.getMessage().contains("跨租户写入被拒"));
        }
    }

    @Test
    public void u2_updateWithoutTenantField_passesThroughUnchanged() {
        asTenantUser();
        ProbeIsolatedEntity entity = isolatedEntity(null);
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertNull("不带值＝不改归属，不得被补位", entity.getTenantId());

        entity.setTenantId(CURRENT);
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertEquals("同租户回写放行", Long.valueOf(CURRENT), entity.getTenantId());
    }

    @Test
    public void u3_updateWithoutCurrentTenant_notBlocked() {
        // 覆盖边界：MP 的 updateFill 也跑在"以实体为参数的读语句"上，
        // 无当前租户时若照样抛会误伤 SELECT——读侧 fail-closed 由改写腿兜（getTenantId 抛）。
        ProbeIsolatedEntity entity = isolatedEntity(FOREIGN);
        handler.updateFill(SystemMetaObject.forObject(entity));
        assertEquals(Long.valueOf(FOREIGN), entity.getTenantId());
    }

    // ==================== A 组：实体字段访问形态（实测事实留档） ====================

    /**
     * 🔴 实测事实（2026-10-20）：iBatis {@code Reflector} 对无访问器字段<b>回落到直接字段访问</b>，
     * 所以本机制<b>不</b>要求标记实体带 getter/setter。
     * <p>写这条之前我先按"必须有访问器"实现了一版 fail-fast 守卫，三条用例全绿不了——
     * 那守卫属恒不触发的死判据，已撤。本例把这个事实钉住，防止后人再凭推断加回去。</p>
     */
    @Test
    public void a1_bareFieldWithoutAccessors_isStillFilled() {
        asTenantUser();
        BareFieldEntity entity = new BareFieldEntity();
        handler.insertFill(SystemMetaObject.forObject(entity));
        assertEquals("无 setter 也要填得上（字段回落）", Long.valueOf(CURRENT), entity.tenantId);
    }

    @Test
    public void a2_bareFieldWithoutGetter_updateLegStillSeesForeignValue() {
        asTenantUser();
        BareFieldEntity entity = new BareFieldEntity();
        entity.tenantId = FOREIGN;
        try {
            handler.updateFill(SystemMetaObject.forObject(entity));
            fail("无 getter 也必须读到声明租户——读不到就是漏闸");
        } catch (ServiceException expected) {
            assertTrue("消息须点明跨租户：" + expected.getMessage(), expected.getMessage().contains("跨租户写入被拒"));
        }
    }

    @Test
    public void a3_bareField_missingValueInsertsWithoutTenant_rejected() {
        // 负向腿配对 a1：无当前租户时，裸字段实体同样吃硬闸（证明闸不依赖访问器形态）
        BareFieldEntity entity = new BareFieldEntity();
        try {
            handler.insertFill(SystemMetaObject.forObject(entity));
            fail("无当前租户必须拒");
        } catch (ServiceException expected) {
            assertTrue(expected.getMessage().contains("未指定租户"));
        }
    }

    /**
     * 异常族分界（2026-10-20 用户拍板）：本条是<b>开发期契约</b>故障（实体属性类型写错），
     * 用户解不了，故钉住 IllegalStateException——不许为了"前端能看"把它顺手改成业务异常，
     * 那会把我们的 bug 伪装成用户的操作不对。i3/u1 那类"切个租户就能解"的拒绝才走 ServiceException。
     */
    @Test
    public void a4_wrongTenantIdType_throws() {
        asTenantUser();
        try {
            handler.insertFill(SystemMetaObject.forObject(new WrongTypeEntity()));
            fail("租户值类型非 Long 时比对不可信，必须抛");
        } catch (IllegalStateException expected) {
            assertTrue("消息须带实际类型：" + expected.getMessage(),
                    expected.getMessage().contains(String.class.getName()));
        }
    }

    // ==================== 判定面自证 ====================

    @Test
    public void z1_isIsolatedEntity_onlyMarkedOnes() {
        assertTrue(TenantIsolationFillHandler.isIsolatedEntity(SystemMetaObject.forObject(isolatedEntity(null))));
        assertFalse(TenantIsolationFillHandler.isIsolatedEntity(SystemMetaObject.forObject(new ProbeFreeEntity())));
        // MP 把包装参数交下来时原对象可能是 Map（ParamMap），instanceof 判据必须为 false
        assertFalse("Map 原对象不得误判为隔离实体",
                TenantIsolationFillHandler.isIsolatedEntity(
                        SystemMetaObject.forObject(new java.util.HashMap<String, Object>())));
    }

    private void assertRejected(Runnable action) {
        try {
            action.run();
            fail("必须抛（fail-closed）");
        } catch (ServiceException expected) {
            assertTrue("消息不得为空串", expected.getMessage() != null && !expected.getMessage().isEmpty());
        }
    }

    // ==================== 探针实体（不注册进 TableInfo，避免污染隔离集缓存） ====================

    /** 故意不带 getter/setter：钉住 iBatis 字段回落形态（见 a1/a2） */
    static class BareFieldEntity implements TenantIsolated {
        Long tenantId;
    }

    static class WrongTypeEntity implements TenantIsolated {
        private String tenantId = "not-a-long";

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }
    }
}
