package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcGroup;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

 /**
 * Title: AcGroupMapper
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/05/08 09:51
 */
@Mapper
public interface AcGroupMapper extends BaseMapper<AcGroup>, com.bidr.kernel.mybatis.mapper.MyBaseMapper<AcGroup> {
}