package com.bidr.authorization.vo.sync;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: SyncEventRes
 * Description: 变更事件响应体（T3.3a）——{@code GET /open-api/v1/sync/events} 的 DTO，
 * 与 {@code sync_outbox} 行一一对应（只去掉落库时刻 {@code create_at}：对端按 {@code occurredAt}
 * 排序与判新旧，落库时刻对它没有意义，多给一面就是多一个会被误用的字段）。
 * <p>
 * 字段即增量契约：对端把本次响应的最后一个 {@code id} 存成下次的 {@code cursor}，
 * 所以 {@code id} 必须原样透出、且响应严格升序。
 * </p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "变更事件（发件箱条目）")
@Data
public class SyncEventRes {

    /**
     * 事件 id（同时是下次拉取的游标）
     */
    @ApiModelProperty(value = "事件 id（同时是下次拉取的游标：把最后一条的 id 原样回传即可）")
    private Long id;

    /**
     * 租户归属（空＝全局事件）
     */
    @ApiModelProperty(value = "租户归属；空＝全局事件")
    private Long tenantId;

    /**
     * 聚合类型
     */
    @ApiModelProperty(value = "聚合类型（USER/ORG/PROJECT 等）")
    private String aggType;

    /**
     * 聚合标识
     */
    @ApiModelProperty(value = "聚合标识（被变更实体主键）")
    private Long aggId;

    /**
     * 事件类型
     */
    @ApiModelProperty(value = "事件类型（CREATED/UPDATED/DELETED 等）")
    private String eventType;

    /**
     * 事件载荷 JSON
     */
    @ApiModelProperty(value = "事件载荷 JSON（核心不解析，原样转发）")
    private String payload;

    /**
     * 事件发生时刻
     */
    @ApiModelProperty(value = "事件发生时刻")
    private Date occurredAt;
}