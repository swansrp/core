package com.bidr.authorization.service.init;

import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcRoleMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.dao.repository.AcRoleMenuService;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AdminInitService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 09:40
 */
@Service
@RequiredArgsConstructor
public class AdminInitService {

    private final SysConfigCacheService sysConfigCacheService;
    private final AcRoleMenuService acRoleMenuService;
    private final AcMenuService acMenuService;

    @Transactional(rollbackFor = Exception.class)
    public void initAdminRole() {
        List<AcRoleMenu> roleMenuList = new ArrayList<>();
        Long adminRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_ADMIN_ROLE_ID);
        List<AcMenu> list = acMenuService.list();
        for (AcMenu acMenu : list) {
            roleMenuList.add(new AcRoleMenu(adminRoleId, acMenu.getMenuId()));
        }
        acRoleMenuService.saveBatch(roleMenuList);
    }
}
