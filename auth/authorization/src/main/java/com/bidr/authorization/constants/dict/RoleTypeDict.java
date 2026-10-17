package com.bidr.authorization.constants.dict;


import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.kernel.constant.dict.MetaDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.constant.dict.IDynamicDict;
import com.bidr.platform.dao.entity.SysDict;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Sharp
 * @since 2026/1/23 10:12
 */
@Component
@RequiredArgsConstructor
@MetaDict(value = "ROLE_TYPE_DICT", remark = "系统角色字典")
public class RoleTypeDict implements IDynamicDict {
    private final AcRoleService acRoleService;

    @Override
    public Collection<SysDict> generate() {
        List<SysDict> resList = new ArrayList<>();
        List<AcRole> allRoles = acRoleService.selectAll();
        if (FuncUtil.isNotEmpty(allRoles)) {
            int i = 0;
            for (AcRole role : allRoles) {
                SysDict dict = buildSysDict(role.getRoleId(), role.getRoleName(), i++);
                resList.add(dict);
            }
        }
        return resList;
    }


}