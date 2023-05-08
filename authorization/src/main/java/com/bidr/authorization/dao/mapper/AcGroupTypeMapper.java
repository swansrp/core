package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcGroupType;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Title: AcGroupTypeMapper
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/08 09:51
 */
@Mapper
public interface AcGroupTypeMapper extends BaseMapper<AcGroupType>, MyBaseMapper<AcGroupType> {
}