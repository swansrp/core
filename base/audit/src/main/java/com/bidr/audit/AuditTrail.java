package com.bidr.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解：标注在业务方法（或<b>控制器类</b>作为共享映射面）上，方法成功返回后由
 * {@link AuditTrailAspect} 组装 {@link AuditRecord} 并写入 {@link AuditTrailSink}
 * （SPI——core 定义、业务装实现）。
 * <p>审计失败（sink 缺失/写入抛错/SpEL 求值失败）一律吞并：不回滚业务、不改变返回值，
 * 走 log.error 高等级＋运维告警事件（{@code ServiceExceptionEvent}，ERROR 级）——裁定 2。</p>
 *
 * <p><b>类级标注＝共享映射面</b>：{@code @Target} 含 TYPE。控制器类级声明 {@code bizType}
 * /{@code visibility}/{@code group} 等供全部写尾段共享，方法级同名属性覆盖类级
 * （{@code AnnotatedElementUtils} 合并语义）。默认拦截模式
 * （{@code bidr.audit-trail.auto.enabled=true}，{@code AuditTrailAutoAdvisor}）里类级标注
 * 还兼作框架继承写面的放行闸；显式注解切面的切点仍只挂方法级。</p>
 *
 * <p><b>SpEL 可用变量</b>（{@code bizId}/{@code group} 整体是表达式；{@code reason}/
 * {@code resourceLabel}/{@code detail} 是 {@code #{...}} 模板）：
 * <ul>
 *   <li>{@code #参数名}——方法入参按名引用（编译带 debug 信息时经参数名发现器解析；
 *       解析不到时可用 {@code #a0}..{@code #aN} 按位兜底）；</li>
 *   <li>{@code #result}——方法返回值（@Around proceed 之后求值，非 null 时可用）；</li>
 *   <li>{@code #args}——入参数组（按位访问：{@code #args[0]}）；</li>
 *   <li>{@code #ctx}——{@link AuditContext} 快照（Map）：方法体内 put 的运行时上下文，
 *       如 {@code #ctx.get('orderNo')}。</li>
 * </ul>
 * 示例：
 * <pre>{@code
 * @AuditTrail(bizType = "merchant_profile", action = "change", field = "grade",
 *             bizId = "#profileId",
 *             reason = "调整等级 #{#profileId}：#{#ctx.get('oldValue')} → #{#result}")
 * public String changeGrade(Long profileId) { ... }
 * }</pre>
 *
 * <p><b>old_value / new_value 列</b>：注解不设属性——方法体内用
 * {@link AuditContext#OLD_VALUE}/{@link AuditContext#NEW_VALUE} 两个约定键
 * {@link AuditContext#put} 进上下文，切面自动并入审计行（查库才知道旧值的场景由此覆盖）。
 * 同族约定键 {@link AuditContext#LABEL}/{@link AuditContext#DETAIL}/
 * {@link AuditContext#ACTION_OVERRIDE}/{@link AuditContext#GROUP_OVERRIDE}/
 * {@link AuditContext#RESOURCE_ID_OVERRIDE} 分别并入 resource_label / detail /
 * action 覆写 / group 覆写 / biz_id 覆写列，且<b>优先于注解属性</b>。</p>
 *
 * <p><b>id 三级提取兜底（仅默认拦截模式）</b>：{@code bizId} 属性空（或求值为空）时按
 * {@link #idParam} 三级提取：①同名绑定入参；②路径模板变量 {@code {idParam}}；③第一个
 * 入参 {@code getId()} 反射。三级提取需要请求面，<b>只属于默认拦截模式（web 端点）</b>；
 * 显式注解切面（无请求面）不走——bizId 空/求值空＝null，行为可预测（需要 id 就显式写
 * 表达式，或运行时 {@link AuditContext#RESOURCE_ID_OVERRIDE} 覆写）。</p>
 *
 * <p><b>求值失败纪律分级</b>：{@code bizId} 与 {@code reason} 求值失败＝<b>整行丢弃</b>＋
 * log.error＋告警（bizId 是定位关键；reason 是变更理由，残缺即失义——都属严格组）；
 * {@code group}/{@code resourceLabel}/{@code detail} 求值失败＝该字段置 null＋log.error
 * ＋告警，行照发（装饰性字段，丢整行反伤可用性）。</p>
 *
 * <p>审计只记"成功发生的动作"：方法抛异常则本次不产审计行（业务异常原样上抛，审计链路不介入）；
 * 宿主可注册 {@link AuditTrailSuccessPolicy} 把框架"以异常作成功通道"的收尾（如
 * {@code Resp.notice} 族）计为成功：照常落审计行后原样 rethrow。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 * @author sharp (audit 框架增强：类级映射面／活动流水维度／id 提取／成功策略 2026-10-24)
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditTrail {

    /**
     * 业务域裸码（M-8 正源值域：merchant_profile / publish_catalog / base_price /
     * merchant_apply / 各单据——biz_erp_audit_log.biz_type 列）。类级标注时必给，
     * 作为该控制器全部写尾段的共享映射。
     */
    String bizType();

    /**
     * 动作（biz_erp_audit_log.action 列值域：create / disable / enable / change / transition；
     * 活动流水宿主可自带值域如 CREATE/UPLOAD/GRANT——core 不约束）。<b>可留空</b>：
     * 空时按运行时覆写（{@link AuditContext#ACTION_OVERRIDE}）→ 默认拦截模式的
     * 路径尾段/方法名推导（insert/add→CREATE、update→UPDATE、delete→DELETE）取值；
     * 显式注解模式留空且无覆写则落 null，由 sink 定夺。
     */
    String action() default "";

    /**
     * 改的是哪一列（biz_erp_audit_log.field_name 列；无列语义的动作留空）。
     */
    String field() default "";

    /**
     * 业务对象标识（biz_erp_audit_log.biz_id 列；单据用 doc_no，主体用 id）。
     * <b>整体是 SpEL 表达式</b>（可用变量见类 javadoc），求值结果转 String；
     * 求值失败＝本次审计行丢弃＋log.error＋告警（不炸业务）。空/求值空时：默认拦截模式走
     * {@link #idParam} 三级提取兜底，显式注解模式＝null（无反射腿）。
     */
    String bizId() default "";

    /**
     * 原因（biz_erp_audit_log.reason 列）。<b>{@code #{...}} 模板＋SpEL 插值</b>
     * （Spring TemplateParserContext，无表达式的纯文本原样落列）。<b>严格求值</b>：
     * 求值失败＝整行丢弃＋告警（变更理由残缺即失义，与 bizId 同组）。
     */
    String reason() default "";

    /**
     * 操作身份字面量（biz_erp_audit_log.operator_identity 列值域：staff / merchant / platform；
     * 操作人主键取 {@code AccountContext.getOperator()}，注解不设属性）。
     */
    String operatorIdentity() default "";

    /**
     * 分组键（泛化的"空间/项目"维度，活动流水宿主用来落 space_id 类列；无分组语义留空）。
     * <b>整体是 SpEL 表达式</b>。运行时优先级：{@link AuditContext#GROUP_OVERRIDE} 覆写 ＞
     * 本属性求值 ＞ {@link AuditTrailGroupResolver} 宿主解析器 ＞（默认拦截模式）
     * 绑定入参/路径模板变量按 {@code bidr.audit-trail.auto.group-param}（缺省 spaceId）兜底。
     */
    String group() default "";

    /**
     * 资源简述（人话文案源，如文件名/页面名——活动流水宿主的 resource_label 类列）。
     * {@code #{...}} 模板插值；{@link AuditContext#LABEL} 运行时值优先。
     */
    String resourceLabel() default "";

    /**
     * 业务语义详情（"做了什么"的人话补充，无请求体原文）。{@code #{...}} 模板插值；
     * {@link AuditContext#DETAIL} 运行时值（多次追加、分号连接）优先。
     */
    String detail() default "";

    /**
     * 可见性档字面量（活动流水宿主自定义值域，如 COLLAB/ADMIN——core 不解释，
     * 透传给 sink）。默认拦截模式里类级标注常用来声明该控制器写面的共享档。
     */
    String visibility() default "";

    /**
     * id 三级提取的参数名（详见类 javadoc"id 三级提取兜底"节；默认 {@code id}）。
     */
    String idParam() default "id";
}
