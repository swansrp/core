package com.bidr.identity.mirror.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.identity.mirror.dao.entity.IdmMirrorState;
import com.bidr.identity.mirror.dao.mapper.IdmMirrorStateMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * Title: IdmMirrorStateService
 * Description: 镜像同步状态 repository（T2.3d）——水位 KV 的读写口。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Service
public class IdmMirrorStateService extends BaseSqlRepo<IdmMirrorStateMapper, IdmMirrorState> {

    /**
     * 按状态键取状态行（UK(state_key)，至多一行）。
     *
     * @param stateKey 状态键（null/空 ⇒ null，不抛）
     * @return 状态行；未写过 ⇒ null
     */
    public IdmMirrorState getByKey(String stateKey) {
        if (stateKey == null || stateKey.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<IdmMirrorState> wrapper = super.getQueryWrapper()
                .eq(IdmMirrorState::getStateKey, stateKey);
        return selectOne(wrapper);
    }
}
