package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcSubsystem;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcSubsystemMapper
 * Description: 子系统注册表 mapper（空接口；包形亦命中 BaseApplication @MapperScan 默认）
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Mapper
public interface AcSubsystemMapper extends MyBaseMapper<AcSubsystem> {
}
