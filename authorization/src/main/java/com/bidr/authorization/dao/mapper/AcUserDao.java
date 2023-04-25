package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcUserDao
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/23 11:31
 */
@Mapper
public interface AcUserDao extends MyBaseMapper<AcUser> {
}
