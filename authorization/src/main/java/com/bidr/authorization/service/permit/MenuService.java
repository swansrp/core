package com.bidr.authorization.service.permit;

import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.vo.menu.MenuTreeItem;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: MenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/13 15:05
 */
@Service
public class MenuService {
    @Resource
    private AcMenuService acMenuService;

    public List<MenuTreeRes> getMenuTree() {
        List<AcMenu> menuList = acMenuService.getAllMenu();
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getMenuList() {
        List<AcMenu> allPermit = acMenuService.getAllMenu();
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        List<AcMenu> menuList = acMenuService.getSubMenu(req.getMenuId());
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getSubMenuList(MenuTreeReq req) {
        List<AcMenu> allPermit = acMenuService.getSubMenu(req.getMenuId());
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeRes> getContentTree(MenuTreeReq req) {
        List<AcMenu> menuList = acMenuService.getContent(req.getMenuId());
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeItem> getContentList(MenuTreeReq req) {
        List<AcMenu> allPermit = acMenuService.getContent(req.getMenuId());
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }

    public List<MenuTreeItem> getAll() {
        List<AcMenu> allPermit = acMenuService.select();
        return ReflectionUtil.copyList(allPermit, MenuTreeItem.class);
    }
}
