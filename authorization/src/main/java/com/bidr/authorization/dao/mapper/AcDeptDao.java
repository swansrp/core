package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * Title: AcDeptDao
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/04 10:25
 */
@Mapper
public interface AcDeptDao extends MyBaseMapper<AcDept> {
    @Update("truncate table `ac_dept`")
    void truncate();
}
