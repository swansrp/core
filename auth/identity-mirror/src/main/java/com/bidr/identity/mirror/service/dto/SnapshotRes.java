package com.bidr.identity.mirror.service.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: SnapshotRes
 * Description: 身份快照 DTO（T2.3d，consumers 侧自带）——provider 端点
 * {@code GET /open-api/v1/identity/snapshot} 的响应体形状，镜像模块按本形状反序列化。
 * <p>🔴 G9 边界的 DTO 面：只有「这是谁/属于哪个租户」，<b>零角色/菜单/功能码字段</b>——
 * 授权判定一律回 provider，快照不带授权语义。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Data
public class SnapshotRes {

    /** 快照生成时刻（provider 侧 epoch millis；全量快照无增量语义，仅作水位标记） */
    private long watermark;

    /** 有效用户全量 */
    private List<UserItem> users = new ArrayList<>();

    /** 有效租户全量 */
    private List<TenantItem> tenants = new ArrayList<>();

    /** 用户-租户归属关系全量 */
    private List<UserTenantItem> userTenants = new ArrayList<>();

    /**
     * 用户条目：id（＝上游用户 id）＋customerNumber＋userName＋name，仅此四面
     * （T2.4a 增登录名——子系统按登录名对齐账号）。
     */
    @Data
    public static class UserItem {

        private Long id;

        private String customerNumber;

        private String userName;

        private String name;
    }

    /**
     * 租户条目：id（＝上游租户 id）＋tenantCode＋tenantName，仅此三面。
     */
    @Data
    public static class TenantItem {

        private Long id;

        private String tenantCode;

        private String tenantName;
    }

    /**
     * 归属关系条目：userId＋tenantId，仅此两面。
     */
    @Data
    public static class UserTenantItem {

        private Long userId;

        private Long tenantId;
    }
}
