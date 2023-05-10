package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

 /**
 * Title: AcGroupTypeDao
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/09 13:24
 */
@Mapper
public interface AcGroupTypeDao extends BaseMapper<AcGroupType>, com.bidr.kernel.mybatis.mapper.MyBaseMapper<AcGroupType> {
}