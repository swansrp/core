package com.bidr.authorization.service.permit;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

import static com.bidr.authorization.constants.param.AccountParam.ACCOUNT_ADMIN_ROLE_ID;

/**
 * Title: PermitService
 * Description: 平台管理员（"isAdmin"）唯一判定入口——全平台"我是不是平台管理员"只认这一处实现。
 * <p><b>收口背景（2026-11-01）</b>：此前同一语义有两份实现（本类 isAdmin 的 containsKey 与
 * {@code AuthAdmin#matchesAdminRole} 的遍历比对），差异只在"AccountContext 为空时是否回落 token"——
 * 导致同一超管在 HTTP 请求里与无上下文线程（定时任务/异步）中可能得到不同答案。
 * 现统一为：<b>本类 isAdmin() 是唯一对外入口</b>（bean 名 permitService 不得改，kernel 侧按名反射调用），
 * 比对逻辑收进静态纯函数 {@link #containsAdminRole}，{@code AuthAdmin#matchesAdminRole} 仅作兼容委托。</p>
 * <p><b>数据源</b>：sys_config 参数 {@link com.bidr.authorization.constants.param.AccountParam#ACCOUNT_ADMIN_ROLE_ID}
 * （默认 "1"）× 用户角色映射 ROLE_MAP（登录时烤入 token；来源 ac_user_role ⨝ ac_role）。
 * 改该参数即可更换"哪个角色算平台管理员"。</p>
 * Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 14:13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermitService {

    private final SysConfigCacheService sysConfigCacheService;
    private final TokenService tokenService;

    /**
     * 是否平台管理员。
     * <p>取数优先级：AccountContext（请求线程已建上下文）→ 回落 token 的 ROLE_MAP
     * （无上下文线程／上下文未建时，沿用历史行为，不外抛）。两路都用同一静态比对。</p>
     */
    public boolean isAdmin() {
        Long adminRoleId = sysConfigCacheService.getParamLong(ACCOUNT_ADMIN_ROLE_ID);
        return containsAdminRole(resolveRoleInfoMap(), adminRoleId);
    }

    /**
     * 唯一比对实现（纯函数，无 Spring 依赖）：角色映射里是否含平台管理员角色。
     * <p>主判 containsKey（映射以 roleId 为键），再遍历 values 兜底——
     * 兼容映射被其他序列化路径重建后键类型漂移的形态，两条都为假才算非管理员。</p>
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

    /** 角色映射取数：上下文优先，空则回落 token（token 取不到时返回 null，由比对函数判 false）。 */
    private Map<Long, RoleInfo> resolveRoleInfoMap() {
        AccountInfo info = AccountContext.get();
        if (FuncUtil.isNotEmpty(info) && FuncUtil.isNotEmpty(info.getRoleInfoMap())) {
            return info.getRoleInfoMap();
        }
        return tokenService.getItem(TokenItem.ROLE_MAP.name(), Map.class, Long.class, RoleInfo.class);
    }
}