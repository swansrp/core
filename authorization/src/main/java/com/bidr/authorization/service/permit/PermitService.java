package com.bidr.authorization.service.permit;

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
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
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

    public boolean isAdmin() {
        Long adminRoleId = sysConfigCacheService.getParamLong(ACCOUNT_ADMIN_ROLE_ID);
        Map<Long, RoleInfo> roleInfoMap;
        if (FuncUtil.isNotEmpty(AccountContext.get())) {
            roleInfoMap = AccountContext.get().getRoleInfoMap();
        } else {
            roleInfoMap = tokenService.getItem(TokenItem.ROLE_MAP.name(), Map.class, Long.class, RoleInfo.class);
        }
        if (FuncUtil.isNotEmpty(roleInfoMap)) {
            return roleInfoMap.containsKey(adminRoleId);
        } else {
            return false;
        }
    }
}
