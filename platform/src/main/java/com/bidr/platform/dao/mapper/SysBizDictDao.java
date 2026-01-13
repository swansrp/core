package com.bidr.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.platform.dao.entity.SysBizDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字典表 Mapper
 *
 * @author sharp
 */
@Mapper
public interface SysBizDictDao extends BaseMapper<SysBizDict>, MyBaseMapper<SysBizDict> {
}
