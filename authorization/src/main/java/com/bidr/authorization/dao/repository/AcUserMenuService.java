package com.bidr.authorization.dao.repository;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.entity.AcUserMenu;
import com.bidr.authorization.dao.mapper.AcUserMenuDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<AcMenu> getAllMenu(Long userId, String clientType) {
        MPJLambdaWrapper<AcUserMenu> wrapper = super.getMPJLambdaWrapper();
        wrapper.leftJoin(AcMenu.class, AcMenu::getMenuId, AcUserMenu::getMenuId);
        wrapper.eq(AcUserMenu::getUserId, userId);
        wrapper.eq(AcMenu::getClientType, clientType);
        wrapper.eq(AcMenu::getStatus, CommonConst.YES);
        wrapper.eq(AcMenu::getVisible, CommonConst.YES);
        wrapper.orderByAsc(AcMenu::getShowOrder);
        return super.selectJoinList(AcMenu.class, wrapper);
    }
}
