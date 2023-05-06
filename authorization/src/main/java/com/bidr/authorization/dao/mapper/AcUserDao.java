package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcUserDao
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/04 10:50
 */
@Mapper
public interface AcUserDao extends MyBaseMapper<AcUser> {
}
