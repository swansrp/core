package com.bidr.kernel.mybatis.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: SyncOutbox
 * Description: 变更事件发件箱（T3.3a）——业务改完数据后追加一行事件，外部系统凭机器凭证按 id 游标增量拉取。
 * <p>
 * 这张表只追加不改写：{@code id} 同时是游标，所以没有任何"补偿/更新"入口；
 * {@code random} 之类让主键不单调的生成策略、以及物理删以外的一致性修补，都会破坏增量语义。
 * </p>
 * <p>
 * {@code tenantId} 可为空（空＝全局事件）：拉取口是系统级机器凭证、不按租户过滤（该身份本来不带租户），
 * 它只决定消费方怎么解释事件归属，不参与拉取裁剪。
 * {@code payload} 核心不解析，原样转发——核心不认识业务字段，解析只会把核心绑死在业务 schema 上。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "变更事件发件箱")
@Data
@TableName(value = "sync_outbox")
public class SyncOutbox {

    /**
     * id（同时是增量游标）
     */
    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty(value = "id（同时是增量游标：只增不改，按 id 升序拉取）")
    private Long id;

    /**
     * 租户归属（空＝全局事件）
     */
    @TableField(value = "tenant_id")
    @ApiModelProperty(value = "租户归属（建表即带；空＝全局事件）")
    private Long tenantId;

    /**
     * 聚合类型
     */
    @TableField(value = "agg_type")
    @ApiModelProperty(value = "聚合类型（USER/ORG/PROJECT 等，消费方自定义字符串）")
    private String aggType;

    /**
     * 聚合标识
     */
    @TableField(value = "agg_id")
    @ApiModelProperty(value = "聚合标识（被变更实体主键）")
    private Long aggId;

    /**
     * 事件类型
     */
    @TableField(value = "event_type")
    @ApiModelProperty(value = "事件类型（CREATED/UPDATED/DELETED 等）")
    private String eventType;

    /**
     * 事件载荷 JSON
     */
    @TableField(value = "payload")
    @ApiModelProperty(value = "事件载荷 JSON（核心不解析，原样转发）")
    private String payload;

    /**
     * 事件发生时刻
     */
    @TableField(value = "occurred_at")
    @ApiModelProperty(value = "事件发生时刻（保留期按它算，不是落库时刻）")
    private Date occurredAt;

    /**
     * 落库时刻
     */
    @TableField(value = "create_at")
    @ApiModelProperty(value = "落库时刻（与 occurred_at 错开时以 occurred_at 为准）")
    private Date createAt;
}