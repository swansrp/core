package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.mapper.AcMenuDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcMenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/17 10:11
 */
@Service
public class AcMenuService extends BaseSqlRepo<AcMenuDao, AcMenu> {

    public List<AcMenu> getMainMenu() {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper()
                .eq(AcMenu::getMenuType, MenuTypeDict.MENU.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderBy(true, true, AcMenu::getShowOrder);
        return super.select(wrapper);
    }

    public List<AcMenu> getAllMenu() {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper()
                .eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderBy(true, true, AcMenu::getShowOrder);
        return super.select(wrapper);
    }

    public List<AcMenu> getSubMenu(Long menuId) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getGrandId, menuId)
                .eq(AcMenu::getMenuType, MenuTypeDict.SUB_MENU.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderBy(true, true, AcMenu::getShowOrder);
        return super.select(wrapper);
    }

    public List<AcMenu> getContent(Long menuId) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getPid, menuId)
                .eq(AcMenu::getMenuType, MenuTypeDict.CONTENT.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderBy(true, true, AcMenu::getShowOrder);
        return super.select(wrapper);
    }
}






