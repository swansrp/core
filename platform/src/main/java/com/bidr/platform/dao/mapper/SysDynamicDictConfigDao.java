package com.bidr.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.platform.dao.entity.SysDynamicDictConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态字典配置 Mapper
 *
 * @author Sharp
 * @since 2026-07-14
 */
@Mapper
public interface SysDynamicDictConfigDao extends BaseMapper<SysDynamicDictConfig>, MyBaseMapper<SysDynamicDictConfig> {
}
