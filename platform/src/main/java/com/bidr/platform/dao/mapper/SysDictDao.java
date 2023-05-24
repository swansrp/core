package com.bidr.platform.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import com.bidr.platform.dao.entity.SysDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: SysDictDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 13:33
 */
@Mapper
public interface SysDictDao extends BaseMapper<SysDict>, MyBaseMapper<SysDict> {
}