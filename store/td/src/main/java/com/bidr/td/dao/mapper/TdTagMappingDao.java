package com.bidr.td.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.td.dao.entity.TdTagMapping;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TdTagMappingDao extends BaseMapper<TdTagMapping>, MyBaseMapper<TdTagMapping> {
}
