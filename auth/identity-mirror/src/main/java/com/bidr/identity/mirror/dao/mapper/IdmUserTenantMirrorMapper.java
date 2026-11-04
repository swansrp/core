package com.bidr.identity.mirror.dao.mapper;

import com.bidr.identity.mirror.dao.entity.IdmUserTenantMirror;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: IdmUserTenantMirrorMapper
 * Description: 用户租户关系镜像 mapper（包形命中 @MapperScan 默认，同 {@link IdmUserMirrorMapper}）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Mapper
public interface IdmUserTenantMirrorMapper extends MyBaseMapper<IdmUserTenantMirror> {
}
