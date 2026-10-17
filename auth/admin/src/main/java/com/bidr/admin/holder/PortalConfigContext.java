package com.bidr.admin.holder;

import com.bidr.admin.service.PortalConfigService;
import com.bidr.admin.constant.token.PortalTokenItem;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.utils.JsonUtil;

/**
 * Title: PortalConfigContext
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/03/10 19:23
 */
public class PortalConfigContext {
    public static Long getPortalConfigRoleId() {
        Long roleId = PortalConfigService.DEFAULT_CONFIG_ROLE_ID;
        try {
            roleId = JsonUtil.readJson(AccountContext.getExtraData()
                    .getOrDefault(PortalTokenItem.PORTAL_ROLE, PortalConfigService.DEFAULT_CONFIG_ROLE_ID), Long.class);
            return roleId;
        } catch (Exception e) {
            return roleId;
        }
    }
}
