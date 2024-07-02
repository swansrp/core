package com.bidr.admin.constant.dict;

import com.bidr.admin.dao.entity.SysPortal;
import com.bidr.admin.dao.repository.SysPortalService;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Title: AllPortalDict
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/7/2 14:53
 */
@Component
@MetaDict(value = "PORTAL_ENTITY_DICT", remark = "系统实体列表字典")
@RequiredArgsConstructor
public class AllPortalDict implements IDynamicDict {

    private final SysPortalService sysPortalService;

    @Override
    public Collection<SysDict> generate() {
        List<SysDict> resList = new ArrayList<>();
        List<SysPortal> portalList = sysPortalService.getAllPortalList();
        if (FuncUtil.isNotEmpty(portalList)) {
            int order = 1;
            for (SysPortal portal : portalList) {
                resList.add(buildSysDict(portal.getId(), portal.getDisplayName(), order++));
            }
        }
        return resList;
    }
}
