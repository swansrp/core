package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcDeptMenu;
import com.bidr.authorization.dao.mapper.AcDeptMenuDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 部门和菜单关联表Service
 *
 * @author sharp
 */
@Service
public class AcDeptMenuService extends BaseSqlRepo<AcDeptMenuDao, AcDeptMenu> {

    /**
     * 绑定菜单到部门
     *
     * @param deptId 部门ID
     * @param menuId 菜单ID
     */
    public void bind(Long deptId, Long menuId) {
        AcDeptMenu acDeptMenu = new AcDeptMenu();
        acDeptMenu.setDeptId(deptId);
        acDeptMenu.setMenuId(menuId);
        super.insertOrUpdate(acDeptMenu);
    }

    /**
     * 解绑部门菜单
     *
     * @param deptId 部门ID
     * @param menuId 菜单ID
     */
    public void unbind(Long deptId, Long menuId) {
        AcDeptMenu acDeptMenu = new AcDeptMenu();
        acDeptMenu.setDeptId(deptId);
        acDeptMenu.setMenuId(menuId);
        super.deleteByMultiId(acDeptMenu);
    }
}
