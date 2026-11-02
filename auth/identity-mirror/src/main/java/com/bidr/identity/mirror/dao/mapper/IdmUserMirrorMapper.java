package com.bidr.identity.mirror.dao.mapper;

import com.bidr.identity.mirror.dao.entity.IdmUserMirror;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: IdmUserMirrorMapper
 * Description: 用户身份镜像 mapper（包形 {@code com.bidr.identity.mirror.dao.mapper}
 * 命中 BaseApplication @MapperScan 默认 {@code com.bidr.**.dao.**.mapper}，M-11）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Mapper
public interface IdmUserMirrorMapper extends MyBaseMapper<IdmUserMirror> {
}
