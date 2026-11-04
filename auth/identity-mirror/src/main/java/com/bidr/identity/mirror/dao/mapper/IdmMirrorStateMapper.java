package com.bidr.identity.mirror.dao.mapper;

import com.bidr.identity.mirror.dao.entity.IdmMirrorState;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: IdmMirrorStateMapper
 * Description: 镜像同步状态 mapper（包形命中 @MapperScan 默认，同 {@link IdmUserMirrorMapper}）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Mapper
public interface IdmMirrorStateMapper extends MyBaseMapper<IdmMirrorState> {
}
