package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcGroupDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/16 09:59
 */
@Mapper
public interface AcGroupDao extends BaseMapper<AcGroup>, MyBaseMapper<AcGroup> {
}