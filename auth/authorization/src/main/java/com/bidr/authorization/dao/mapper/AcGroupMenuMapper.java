package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcGroupMenu;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户组和菜单关联表Mapper
 *
 * @author sharp
 */
@Mapper
public interface AcGroupMenuMapper extends MyBaseMapper<AcGroupMenu> {
}
