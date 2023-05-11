package com.bidr.authorization.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bidr.authorization.dao.entity.AcUserGroup;
import org.apache.ibatis.annotations.Mapper;

 /**
 * Title: AcUserGroupMapper
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/07 20:33
 */
@Mapper
public interface AcUserGroupMapper extends BaseMapper<AcUserGroup>, com.bidr.kernel.mybatis.mapper.MyBaseMapper<AcUserGroup> {
}
