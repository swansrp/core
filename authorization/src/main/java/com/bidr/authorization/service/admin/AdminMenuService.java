package com.bidr.authorization.service.admin;

import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.repository.AcMenuService;
import com.bidr.authorization.vo.menu.MenuTreeReq;
import com.bidr.authorization.vo.menu.MenuTreeRes;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminMenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/24 09:50
 */
@Service
public class AdminMenuService {

    @Resource
    private AcMenuService acMenuService;

    @Transactional(rollbackFor = Exception.class)
    public void addMenu(AcMenu entity, MenuTypeDict menuType) {
        entity.setMenuType(menuType.getValue());
        entity.setStatus(CommonConst.YES);
        entity.setVisible(CommonConst.YES);
        acMenuService.insert(entity);
        entity.setKey(entity.getMenuId());
        acMenuService.updateById(entity);
    }

    public List<MenuTreeRes> getMenuTree() {
        List<AcMenu> menuList = acMenuService.getMainMenu();
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }

    public List<MenuTreeRes> getSubMenuTree(MenuTreeReq req) {
        List<AcMenu> menuList = acMenuService.getSubMenu(req.getMenuId());
        return ReflectionUtil.buildTree(MenuTreeRes::setChildren, menuList, AcMenu::getMenuId, AcMenu::getPid);

    }
}
