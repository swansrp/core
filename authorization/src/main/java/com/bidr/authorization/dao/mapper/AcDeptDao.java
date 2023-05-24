package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

/**
 * Title: AcDeptDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/24 10:03
 */
@Mapper
public interface AcDeptDao extends BaseMapper<AcDept>, MyBaseMapper<AcDept> {
    @Update("truncate table `ac_dept`")
    void truncate();
}