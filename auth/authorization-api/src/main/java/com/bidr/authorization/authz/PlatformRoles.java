package com.bidr.authorization.authz;

import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.kernel.utils.FuncUtil;

import java.util.Map;

/**
 * Title: PlatformRoles
 * Description: 平台管理员角色比对纯函数（T2.3c 自 {@code PermitService.containsAdminRole}
 * 迁入，逻辑逐字平移——2026-11-01 收口裁定的"唯一比对实现"随类型面上提到 api，
 * server/client 双侧共用单一真相）。
 *
 * <p>server 侧 {@code PermitService} 保留 {@code public static containsAdminRole}
 * 兼容签名（既有调用方零改动），实现改一行委托本类；client 侧（{@code AuthLogin}
 * 构建上下文的平台档置位）直接调本类。</p>
 *
 * @author coder (棒1, T2.3c 上提 2026-11-03)
 * @since 2026-11-03
 */
public final class PlatformRoles {

    private PlatformRoles() {
        // 静态工具，禁实例化
    }

    /**
     * 角色映射里是否含平台管理员角色（纯函数，无 Spring 依赖）。
     * <p>主判 containsKey（映射以 roleId 为键），再遍历 values 兜底——
     * 兼容映射被其他序列化路径重建后键类型漂移的形态，两条都为假才算非管理员。</p>
     *
     * @param roleInfoMap 用户角色映射（登录时烤入 token 的 ROLE_MAP / AccountContext 同构面）
     * @param adminRoleId 平台管理员角色 id（sys_config ACCOUNT_ADMIN_ROLE_ID）
     * @return true＝含管理员角色
     */
    public static boolean containsAdminRole(Map<Long, RoleInfo> roleInfoMap, Long adminRoleId) {
        if (FuncUtil.isEmpty(roleInfoMap) || adminRoleId == null) {
            return false;
        }
        if (roleInfoMap.containsKey(adminRoleId)) {
            return true;
        }
        for (RoleInfo role : roleInfoMap.values()) {
            if (role != null && FuncUtil.equals(role.getRoleId(), adminRoleId)) {
                return true;
            }
        }
        return false;
    }
}
