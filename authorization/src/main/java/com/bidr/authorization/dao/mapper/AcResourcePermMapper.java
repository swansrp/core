package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcResourcePerm;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通用资源权限表Mapper
 *
 * @author Sharp
 * @since 2026/07/20
 */
@Mapper
public interface AcResourcePermMapper extends MyBaseMapper<AcResourcePerm> {
}
