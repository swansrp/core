package com.bidr.audit;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * 审计行 SPI 载体（不可变）：切面组装、{@link AuditTrailSink} 消费。
 * <p>字段集对齐 biz_erp_audit_log 列（M-8 正源 {@code BizErpAuditLog}——core 零 import，
 * 只做列名对照）；create_by/create_at/update_by/update_at/valid 由落库侧填充器与 DDL
 * 默认值负责，不在载体上。core 不依赖任何业务表——落库（含豁免段写入）是业务侧 sink 的职责。</p>
 * <p>活动流水维度（groupKey/resourceLabel/detail/visibility/endpoint）为框架增强新增列：
 * ERP 落库实现不消费这些列、原行为零变化；活动流水宿主（如空间审计）的 sink 自行映射到
 * 自有表列（如 space_id/resource_label/detail/vis_scope/endpoint）。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
 * @author sharp (audit 框架增强：活动流水维度列 2026-10-24)
 */
@Value
@Builder
public class AuditRecord {

    /** 业务域裸码 → biz_type */
    String bizType;

    /** 动作 → action */
    String action;

    /** 变更列名 → field_name（无列语义为 null） */
    String field;

    /** 业务对象标识（单据 doc_no / 主体 id）→ biz_id */
    String bizId;

    /** 旧值 → old_value（方法内经 {@link AuditContext#OLD_VALUE} 约定键补充；缺省 null） */
    String oldValue;

    /** 新值 → new_value（方法内经 {@link AuditContext#NEW_VALUE} 约定键补充；缺省 null） */
    String newValue;

    /** 原因（模板插值后文本）→ reason */
    String reason;

    /** 操作人主键（AccountContext.getOperator()，ac_user.customer_number）→ operator_user_id */
    String operatorUserId;

    /** 操作身份（staff / merchant / platform）→ operator_identity */
    String operatorIdentity;

    /** 发生时间（服务端，组装时刻）→ occurred_at */
    Date occurredAt;

    /** 租户；平台级动作（开租户等）为 null → tenant_id */
    Long tenantId;

    /** 分组键（泛化的空间/项目维度；活动流水宿主落 space_id 类列，默认拦截模式提取）→ 宿主自定列 */
    String groupKey;

    /** 资源简述（人话文案源，如文件名；{@link AuditContext#LABEL} 优先）→ 宿主自定列 */
    String resourceLabel;

    /** 业务语义详情（做了什么的人话补充；{@link AuditContext#DETAIL} 优先）→ 宿主自定列 */
    String detail;

    /** 可见性档字面量（值域归宿主 sink 解释；core 不约束）→ 宿主自定列 */
    String visibility;

    /** 来源端点 "METHOD path"（默认拦截模式从请求面提取；非 web 场景 null）→ 宿主自定列 */
    String endpoint;
}
