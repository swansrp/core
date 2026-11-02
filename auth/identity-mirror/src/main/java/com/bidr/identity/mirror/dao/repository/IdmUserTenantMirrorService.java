package com.bidr.identity.mirror.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.identity.mirror.dao.mapper.IdmUserTenantMirrorMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: IdmUserTenantMirrorService
 * Description: 用户租户关系镜像 repository（T2.3d）。同步比对/增删的取数口——
 * 关系镜像按 (userId,tenantId) 全量对齐，不做单边局部维护。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmUserTenantMirrorService extends BaseSqlRepo<IdmUserTenantMirrorMapper, IdmUserTenantMirror> {

    /**
     * 关系镜像全量（同步比对用）。
     *
     * @return 全部关系镜像行，无数据时返回空列表
     */
    public List<IdmUserTenantMirror> listAll() {
        return select();
    }

    /**
     * 删除一条关系镜像行（同步全量比对的"上游已不存在"腿）。
     *
     * @param userId   用户 id（任一 null ⇒ false，不删不抛）
     * @param tenantId 租户 id
     * @return 是否删到行
     */
    public boolean deleteByUserAndTenant(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return false;
        }
        LambdaQueryWrapper<IdmUserTenantMirror> wrapper = super.getQueryWrapper()
                .eq(IdmUserTenantMirror::getUserId, userId)
                .eq(IdmUserTenantMirror::getTenantId, tenantId);
        return delete(wrapper);
    }
}
