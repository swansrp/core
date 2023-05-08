package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.authorization.dao.mapper.AcRoleDao;
import com.bidr.authorization.vo.admin.QueryRoleReq;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

/**
 * Title: AcRoleService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@Service
public class AcRoleService extends BaseSqlRepo<AcRoleDao, AcRole> {

    public Page<AcRole> queryRole(QueryRoleReq req) {
        LambdaQueryWrapper<AcRole> wrapper = super.getQueryWrapper()
                .like(FuncUtil.isNotEmpty(req.getName()), AcRole::getRoleName, req.getName());
        return super.select(wrapper, req);
    }
}






