package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.dao.mapper.AcUserRoleDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcUserRoleService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:02
 */
@Service
public class AcUserRoleService extends BaseSqlRepo<AcUserRoleDao, AcUserRole> {

    public void bind(Long userId, Long roleId) {
        AcUserRole acUserRole = new AcUserRole();
        acUserRole.setUserId(userId);
        acUserRole.setRoleId(roleId);
        super.insertOrUpdate(acUserRole);
    }

    public void unbind(Long userId, Long roleId) {
        AcUserRole acUserRole = new AcUserRole();
        acUserRole.setUserId(userId);
        acUserRole.setRoleId(roleId);
        super.deleteByMultiId(acUserRole);
    }

    public boolean existedByRoleId(String id) {
        LambdaQueryWrapper<AcUserRole> wrapper = super.getQueryWrapper().eq(AcUserRole::getRoleId, id);
        return super.existed(wrapper);
    }

    /**
     * 获取用户角色 id 列表（替代原 @BindFieldList 注解绑定）
     */
    public List<Long> getRoleIdsByUserId(Long userId) {
        LambdaQueryWrapper<AcUserRole> wrapper = super.getQueryWrapper().eq(AcUserRole::getUserId, userId);
        return ReflectionUtil.getFieldList(super.list(wrapper), AcUserRole::getRoleId);
    }
}

