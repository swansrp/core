package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcUserMenu;
import com.bidr.authorization.dao.mapper.AcUserMenuDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 用户和菜单关联表Service
 *
 * @author sharp
 */
@Service
public class AcUserMenuService extends BaseSqlRepo<AcUserMenuDao, AcUserMenu> {

    /**
     * 绑定菜单给用户
     *
     * @param userId 用户ID
     * @param menuId 菜单ID
     */
    public void bind(Long userId, Long menuId) {
        AcUserMenu acUserMenu = new AcUserMenu();
        acUserMenu.setUserId(userId);
        acUserMenu.setMenuId(menuId);
        super.insertOrUpdate(acUserMenu);
    }

    /**
     * 解绑用户菜单
     *
     * @param userId 用户ID
     * @param menuId 菜单ID
     */
    public void unbind(Long userId, Long menuId) {
        AcUserMenu acUserMenu = new AcUserMenu();
        acUserMenu.setUserId(userId);
        acUserMenu.setMenuId(menuId);
        super.deleteByMultiId(acUserMenu);
    }
}
