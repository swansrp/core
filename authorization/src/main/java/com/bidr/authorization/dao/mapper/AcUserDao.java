package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcUserDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 10:04
 */
@Mapper
public interface AcUserDao extends BaseMapper<AcUser>, MyBaseMapper<AcUser> {
}