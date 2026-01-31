package com.bidr.authorization.dao.mapper;

import com.bidr.authorization.dao.entity.AcDeptMenu;
import com.bidr.kernel.mybatis.mapper.MyBaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 部门和菜单关联表Mapper
 *
 * @author sharp
 */
@Mapper
public interface AcDeptMenuDao extends MyBaseMapper<AcDeptMenu> {
}
