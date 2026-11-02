package com.bidr.identity.mirror.dao.repository;

import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.identity.mirror.dao.mapper.IdmTenantMirrorMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: IdmTenantMirrorService
 * Description: 租户身份镜像 repository（T2.3d）。取数口收敛给 {@code IdentityMirrorSyncService}
 * （全量比对）。
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
}
