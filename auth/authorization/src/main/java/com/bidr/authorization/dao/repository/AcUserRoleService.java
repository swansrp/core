package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.authz.AuthzVersionHolder;
import com.bidr.authorization.dao.entity.AcUserRole;
import com.bidr.authorization.dao.mapper.AcUserRoleDao;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcUserRoleService
 * Description: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:11
 */
@Service
public class AcUserRoleService extends AuthzVersionedSqlRepo<AcUserRoleDao, AcUserRole> {

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
        boolean removed = super.deleteByMultiId(acUserRole);
        if (removed) {
            // T2.3b：deleteByMultiId 是 IMppService default 方法，不经 AuthzVersionedSqlRepo
            // 拦截面，此处显式补 bump（用户角色写点，client 缓存失效信号）
            AuthzVersionHolder.bump();
        }
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

