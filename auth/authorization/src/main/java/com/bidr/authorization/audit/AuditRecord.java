package com.bidr.authorization.audit;

import lombok.Builder;
import lombok.Value;

import java.util.Date;

/**
 * 审计行 SPI 载体（不可变）：{@link AuditTrailAspect} 组装、{@link AuditTrailSink} 消费。
 * <p>字段集对齐 biz_erp_audit_log 列（M-8 正源 {@code BizErpAuditLog}——core 零 import，
 * 只做列名对照）；create_by/create_at/update_by/update_at/valid 由落库侧填充器与 DDL
 * 默认值负责，不在载体上。core 不依赖任何业务表——落库（含豁免段写入）是业务侧 sink 的职责。</p>
 *
 * @author coder (棒2, 租户豁免与审计框架治理 2026-10-24)
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
}
