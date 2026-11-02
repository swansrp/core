package com.bidr.identity.mirror.dao.repository;

import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.identity.mirror.dao.mapper.IdmUserMirrorMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: IdmUserMirrorService
 * Description: 用户身份镜像 repository（T2.3d）。取数口收敛给
 * {@code IdentityMirrorSyncService}（全量比对）与 {@code MirrorBindRepoResolver}
 * （@BindUser SPI 兜底查询通道）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmUserMirrorService extends BaseSqlRepo<IdmUserMirrorMapper, IdmUserMirror> {

    /**
     * 镜像全量（同步比对用；镜像表＝上游有效行投影，行集小，直接拉平）。
     *
     * @return 全部镜像行，无数据时返回空列表
     */
    public List<IdmUserMirror> listAll() {
        return select();
    }
}
