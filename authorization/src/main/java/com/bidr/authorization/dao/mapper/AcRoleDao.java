package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcRole;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcRoleDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 10:15
 */
@Mapper
public interface AcRoleDao extends BaseMapper<AcRole>, MyBaseMapper<AcRole> {
}