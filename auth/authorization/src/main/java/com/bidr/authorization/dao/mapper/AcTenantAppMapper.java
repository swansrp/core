package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcTenantApp;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcTenantAppMapper
 * Description: 租户级应用开通表 mapper（空接口；包形亦命中 BaseApplication @MapperScan 默认）
 *
 * @author Sharp
 * @since 2026-11-07
 */
@Mapper
public interface AcTenantAppMapper extends MyBaseMapper<AcTenantApp> {
}
