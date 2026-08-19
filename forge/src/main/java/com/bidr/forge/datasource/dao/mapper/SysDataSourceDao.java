package com.bidr.forge.datasource.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.forge.datasource.dao.entity.SysDataSource;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: SysDataSourceDao
 * Description: 数据源配置表 Mapper
 *
 * @author Sharp
 * @since 2026/8/18
 */
@Mapper
public interface SysDataSourceDao extends BaseMapper<SysDataSource>, MyBaseMapper<SysDataSource> {
}
