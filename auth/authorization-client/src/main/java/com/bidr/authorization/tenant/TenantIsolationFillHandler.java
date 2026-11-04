package com.bidr.authorization.tenant;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.bidr.authorization.holder.TenantContext;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import org.apache.ibatis.reflection.MetaObject;

/**
 * Title: TenantIsolationFillHandler
 * Description: 租户写入校验闸——{@link TenantIsolated} 实体带外租户值、或未选定租户就建数据时
 * 当场拒（2026-10-20 用户裁定），业务写路径不再靠"每处记得 setTenantId"。
 *
 * <p><b>补与拒都在这里，前提是实体把列声明成可填（2026-10-20 真库实测确定）</b>：
 * 实体必须写 {@code @TableField(value = "tenant_id", fill = FieldFill.INSERT)}——普通列走
 * {@code <if test="tenantId != null">} 动态拼列，列清单在 BoundSql 生成期就定型、早于本类
 * （{@code BaseStatementHandler} 先 {@code getBoundSql} 再建 {@code MybatisParameterHandler}），
 * 缺该声明时本类填进实体的值追不上列清单，漏填只剩库侧
 * {@code Field 'tenant_id' doesn't have a default value}；带该声明时列恒出现在 INSERT 里，
 * 参数绑定发生在本类之后，补位才真的落库。声明由
 * {@link TenantLineIsolationConfiguration#assertTenantColumnFillDeclared} 首用 fail-fast 守住。
 * SQL 层自动注入（{@code ignoreInsert} 返回 false）那条路同一轮实测排除：MP 3.5.2 见到
 * {@code VALUES} 形态也转手 {@code processInsertSelect}，cast PlainSelect 直接
 * {@code ClassCastException}。</p>
 *
 * <p><b>口径（用户 2026-10-20 终裁：不指定租户＝不能建数据）</b>：
 * <ul>
 * <li>{@code TenantContext.runWithoutIsolation} 段内＝完全不介入（种子/迁移/对账的合法通道）；</li>
 * <li>INSERT：当前租户缺失＝拒（不给"归属不明的行"落库的机会）；有当前租户＝字段空则填当前租户
 * （列已声明为 {@code fill}，填的值直接落库）、字段非空但≠当前租户＝拒（跨租户写入）；</li>
 * <li>UPDATE：只在"实体带了 tenant_id 且当前租户已选"时比对，不等＝拒（防把行搬出租户面）。
 * 不带值＝不改归属，放行。</li>
 * </ul>
 * 填腿用 {@code fillStrategy}（MP 3.5.2 实测＝空才填，非空不覆盖），因此业务已显式赋值处
 * 天然共存，改造期间不必先删业务代码。</p>
 *
 * <p><b>覆盖边界（不许当成万能闸）</b>：
 * <ol>
 * <li>只覆盖 MP 实体面（{@code insert}/{@code save}/{@code saveBatch}/{@code updateById}/
 * {@code update(entity, wrapper)}）；自定义 XML 的 INSERT 参数是 ParamMap，MP 不触发填充链，
 * 而 SQL 层也不注入（{@code ignoreInsert} 恒 true）——那条腿的 {@code tenant_id} 全靠写语句自己带，
 * 列的 {@code NOT NULL} 是唯一兜底腿，不得撤。</li>
 * <li>{@code wrapper.set(Entity::getTenantId, X)}（{@code et} 为 null）不触发本类。</li>
 * <li>{@code updateFill} 也会跑在"以实体为参数的 SELECT/DELETE"上（MP 只按
 * {@code sqlCommandType==INSERT} 分流），故 UPDATE 腿在当前租户缺失时不介入，
 * 避免误伤读——INSERT 腿无此歧义，硬闸安全。</li>
 * </ol>
 * </p>
 *
 * <p><b>装配</b>：非 {@code @Component}，由 {@link TenantLineIsolationConfiguration}
 * 在 {@code my.tenant.line-enabled=true} 条件下注册为 {@link MetaObjectHandler} bean，
 * 经框架 {@code MetaObjectHandlerManager}（@Primary）聚合生效——未启用项目零影响。</p>
 *
 * <p><b>异常族（2026-10-20 用户拍板，整族一起改、不许只改一半）</b>：
 * <ul>
 * <li><b>用户可触发的拒绝</b>＝未选租户建数据、实体声明别租户值、UPDATE 把行搬出租户面——
 * 一律 {@code ServiceException}（框架口径：只有它的 message 能进前端 details，
 * 用户在顶栏切个租户即可解除，属于「告诉用户该怎么办」的失败）；
 * 同族的读侧 fail-closed 落在 {@code TenantContext.requireCurrentTenantId()}，同一类型。</li>
 * <li><b>开发期契约/机制故障</b>＝实体 {@code tenantId} 值类型非 Long、隔离实体缺列或缺
 * {@code fill} 声明、隔离面 SQL 解析失败——一律 {@code IllegalStateException}：
 * 这些不是用户能解的，改成业务异常反而把「我们的 bug」伪装成「你的操作不对」。</li>
 * </ul>
 * 两类都是 RuntimeException，事务照常回滚。</p>
 *
 * @author Sharp
 * @since 2026-10-20
 */
public class TenantIsolationFillHandler implements MetaObjectHandler {

    /** 隔离实体必带的属性名（守卫：{@link TenantLineIsolationConfiguration#assertIsolatedEntityValid}） */
    static final String TENANT_FIELD = "tenantId";

    // ==================== 写闸（消息正源） ====================

    /** 未选定租户——建数据一律拒（用户终裁的机制落点） */
    static final String NO_TENANT_MSG = "[tenant] 未指定租户，不能建租户数据：请先在顶栏切换到目标租户"
            + "（内部任务段须显式用 TenantContext.runWithoutIsolation 包裹）";

    private static String mismatchMsg(Object declared, Long current) {
        return "[tenant] 跨租户写入被拒：实体声明租户 " + declared + "，当前租户 " + current
                + "——代别租户建/改数据须先切到目标租户，内部治理段用 TenantContext.runWithoutIsolation 包裹";
    }

    /** 用户可触发的拒绝统一从这里出（异常族口径见类注释；换类型只改这一处） */
    private static ServiceException reject(String msg) {
        return new ServiceException(ErrCodeSys.SYS_ERR_MSG, msg);
    }

    /**
     * 读实体声明的租户值（null＝业务未赋值）。
     * <p>不要求实体带 getter/setter：iBatis {@code Reflector} 对无访问器字段回落到直接字段访问
     * （2026-10-20 实测，{@code TenantIsolationFillHandlerTest#a1/a2} 钉住）。
     * 值类型非 Long 一律当场抛——类型不符则 {@code current.equals(declared)} 恒 false，
     * 会被误报成跨租户写入，宁可给出真因。本条属<b>开发期契约</b>族，
     * 故意不走 {@link #reject(String)}（用户解不了，不该伪装成业务拒绝）。</p>
     */
    static Object readDeclaredTenant(MetaObject metaObject) {
        Object declared = metaObject.getValue(TENANT_FIELD);
        if (declared != null && !(declared instanceof Long)) {
            throw new IllegalStateException("[tenant] 隔离实体的 tenantId 必须是 Long，实际为 "
                    + declared.getClass().getName() + "：值类型不符则租户比对不可信");
        }
        return declared;
    }

    // ==================== INSERT ====================

    @Override
    public void insertFill(MetaObject metaObject) {
        if (!isIsolatedEntity(metaObject) || TenantContext.isIsolationOff()) {
            return;
        }
        Long current = TenantContext.currentTenantId();
        if (current == null) {
            throw reject(NO_TENANT_MSG);
        }
        Object declared = readDeclaredTenant(metaObject);
        if (declared == null) {
            fillStrategy(metaObject, TENANT_FIELD, current);
            return;
        }
        if (!current.equals(declared)) {
            throw reject(mismatchMsg(declared, current));
        }
    }

    // ==================== UPDATE ====================

    @Override
    public void updateFill(MetaObject metaObject) {
        if (!isIsolatedEntity(metaObject) || TenantContext.isIsolationOff()) {
            return;
        }
        Object declared = readDeclaredTenant(metaObject);
        if (declared == null) {
            // 不带值＝不改归属（MP 的 <if test="et.tenantId != null"> 不会把它拼进 SET）
            return;
        }
        Long current = TenantContext.currentTenantId();
        if (current == null) {
            // 见类注释覆盖边界③：本腿也会跑在以实体为参数的读语句上，无当前租户时不介入
            return;
        }
        if (!current.equals(declared)) {
            throw reject(mismatchMsg(declared, current));
        }
    }

    // ==================== 内部判定 ====================

    /** 本条填充是否走租户写闸：只认实现标记接口的实体，其余实体零代价跳过 */
    static boolean isIsolatedEntity(MetaObject metaObject) {
        return metaObject.getOriginalObject() instanceof TenantIsolated;
    }
}
