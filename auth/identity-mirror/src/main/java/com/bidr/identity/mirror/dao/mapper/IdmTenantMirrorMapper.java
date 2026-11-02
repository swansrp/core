package com.bidr.identity.mirror.dao.mapper;

import com.bidr.identity.mirror.dao.entity.IdmTenantMirror;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: IdmTenantMirrorMapper
 * Description: 租户身份镜像 mapper（包形命中 @MapperScan 默认，同 {@link IdmUserMirrorMapper}）。
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Mapper
public interface IdmTenantMirrorMapper extends MyBaseMapper<IdmTenantMirror> {
}
