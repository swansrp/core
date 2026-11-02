package com.bidr.authorization.vo.identity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: IdentitySnapshotRes
 * Description: 身份快照响应体（T2.3d，provider 侧）——{@code GET /open-api/v1/identity/snapshot}
 * 的 DTO。全量拉平形态：watermark＋三段全量（用户/租户/归属关系），消费方
 * （identity-mirror）按同形状反序列化做幂等 upsert。
 * <p>🔴 G9 边界（承诺面点名，IdentityMirrorConventionTest 消费侧对偶钉）：本 DTO 只承载
 * 「这是谁/属于哪个租户」的<b>身份面</b>，零角色/菜单/功能码字段——授权判定不走快照，
 * 一律经 /authz 端点回本域实时判。字段面与 consumers 侧
 * {@code com.bidr.identity.mirror.service.dto.SnapshotRes} 逐字段对称，改字段须两侧一起动。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@ApiModel(description = "身份快照（用户/租户/归属关系全量＋水位）")
@Data
public class IdentitySnapshotRes {

    /**
     * 快照生成时刻（epoch millis）——全量快照无增量语义，仅作消费方水位标记
     */
    @ApiModelProperty(value = "快照生成时刻 epoch millis")
    private long watermark;

    /**
     * 有效用户全量（id/customerNumber/name 三面）
     */
    @ApiModelProperty(value = "用户全量")
    private List<UserItem> users = new ArrayList<>();

    /**
     * 有效租户全量（id/tenantCode/tenantName 三面）
     */
    @ApiModelProperty(value = "租户全量")
    private List<TenantItem> tenants = new ArrayList<>();

    /**
     * 用户-租户归属关系全量（userId/tenantId 两面）
     */
    @ApiModelProperty(value = "归属关系全量")
    private List<UserTenantItem> userTenants = new ArrayList<>();

    /**
     * 用户条目（🔴 只允许身份三面：加角色/权限字段前先过 G9 边界裁定）
     */
    @Data
    public static class UserItem {

        @ApiModelProperty(value = "用户id")
        private Long id;

        @ApiModelProperty(value = "用户编码")
        private String customerNumber;

        @ApiModelProperty(value = "用户姓名")
        private String name;
    }

    /**
     * 租户条目（🔴 只允许身份三面）
     */
    @Data
    public static class TenantItem {

        @ApiModelProperty(value = "租户id")
        private Long id;

        @ApiModelProperty(value = "租户业务编码")
        private String tenantCode;

        @ApiModelProperty(value = "租户名称")
        private String tenantName;
    }

    /**
     * 归属关系条目（🔴 只允许归属两面）
     */
    @Data
    public static class UserTenantItem {

        @ApiModelProperty(value = "用户id")
        private Long userId;

        @ApiModelProperty(value = "租户id")
        private Long tenantId;
    }
}
