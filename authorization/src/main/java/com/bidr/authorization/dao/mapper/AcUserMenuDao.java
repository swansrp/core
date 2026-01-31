package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcUserMenu;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户和菜单关联表Mapper
 *
 * @author sharp
 */
@Mapper
public interface AcUserMenuDao extends MyBaseMapper<AcUserMenu> {
}
