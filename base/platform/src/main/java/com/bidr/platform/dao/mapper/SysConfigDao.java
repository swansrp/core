package com.bidr.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.platform.dao.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: SysConfigDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 13:32
 */
@Mapper
public interface SysConfigDao extends BaseMapper<SysConfig>, MyBaseMapper<SysConfig> {
}