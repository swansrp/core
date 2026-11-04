package com.bidr.identity.mirror.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.mapper.IdmTenantMirrorMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: IdmTenantMirrorService
 * Description: 租户身份镜像 repository（T2.3d）。取数口收敛给 {@code IdentityMirrorSyncService}
 * （全量比对/单用户作用域写）；业务码查询供子系统按租户编码对齐。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmTenantMirrorService extends BaseSqlRepo<IdmTenantMirrorMapper, IdmTenantMirror> {

    /**
     * 镜像全量（同步比对用）。
     *
     * @return 全部镜像行，无数据时返回空列表
     */
    public List<IdmTenantMirror> listAll() {
        return select();
    }

    /**
     * 按租户业务编码取镜像行（UK(tenant_code)，至多一行；登录链 best-effort，不抛）。
     *
     * @param tenantCode 租户业务编码（null/空 ⇒ null）
     * @return 镜像行；无此租户 ⇒ null
     */
    public IdmTenantMirror findByTenantCode(String tenantCode) {
        if (tenantCode == null || tenantCode.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<IdmTenantMirror> wrapper = super.getQueryWrapper()
                .eq(IdmTenantMirror::getTenantCode, tenantCode);
        return selectOne(wrapper);
    }
}
