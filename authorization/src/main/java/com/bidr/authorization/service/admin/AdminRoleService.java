package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.repository.AcRoleService;
import com.bidr.authorization.vo.admin.QueryRoleReq;
import com.bidr.authorization.vo.admin.RoleReq;
import com.bidr.authorization.vo.admin.RoleRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Title: AdminRoleService
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/05 09:10
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final AcRoleService acRoleService;

    public Page<RoleRes> queryRole(QueryRoleReq req) {
        Page<AcRole> res = acRoleService.queryRole(req);
        return Resp.convert(res, RoleRes.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public void addRole(RoleReq req) {
        AcRole role = ReflectionUtil.copy(req, AcRole.class);
        role.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        acRoleService.insert(role);
    }
}
