package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * Title: AcAccountDao
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/23 13:34
 */
@Mapper
public interface AcAccountDao extends MyBaseMapper<AcAccount> {
    @Update("truncate table `ac_account`")
    void truncate();
}
