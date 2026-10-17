package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.mapper.AcMenuDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcMenuService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:11
 */
@Service
public class AcMenuService extends BaseSqlRepo<AcMenuDao, AcMenu> {

    public List<AcMenu> getMainMenu() {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper()
                .eq(AcMenu::getMenuType, MenuTypeDict.MENU.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderBy(true, true, AcMenu::getShowOrder);
        return super.select(wrapper);
    }

    public List<AcMenu> getAllMenu(boolean isAdmin) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        if (!isAdmin) {
            wrapper.ne(AcMenu::getMenuId, 1);
            wrapper.or(or -> or.ne(AcMenu::getGrandId, 1).isNull(AcMenu::getGrandId));
        }
        return super.select(wrapper);
    }

    public List<AcMenu> getSubMenu(Long menuId) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getGrandId, menuId)
                .in(AcMenu::getMenuType, MenuTypeDict.SUB_MENU.getValue(), MenuTypeDict.BUTTON.getValue())
                .eq(AcMenu::getStatus, CommonConst.YES).eq(AcMenu::getVisible, CommonConst.YES)
                .orderByAsc(AcMenu::getShowOrder);
        return super.select(wrapper);
    }

    public List<AcMenu> getContent(Long menuId) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getPid, menuId)
                .eq(AcMenu::getMenuType, MenuTypeDict.CONTENT.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        return super.select(wrapper);
    }

    public int countByPid(Long pid) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(pid), AcMenu::getPid, pid);
        wrapper.isNull(FuncUtil.isEmpty(pid), AcMenu::getPid);
        return new Long(super.count(wrapper)).intValue();
    }

    public int countByGrandId(Long grandId) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(grandId), AcMenu::getGrandId, grandId);
        wrapper.isNull(FuncUtil.isEmpty(grandId), AcMenu::getPid);
        return new Long(super.count(wrapper)).intValue();
    }

    /**
     * 根据路径和父ID查询菜单
     *
     * @param path 路径片段
     * @param pid  父菜单ID，可为null
     * @return 匹配的菜单项，未找到返回null
     */
    public AcMenu findByPathAndPid(String path, Long pid, Long grandId) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(pid), AcMenu::getPid, pid);
        wrapper.isNull(FuncUtil.isEmpty(pid), AcMenu::getPid);
        wrapper.eq(FuncUtil.isNotEmpty(grandId), AcMenu::getGrandId, grandId);
        wrapper.isNull(FuncUtil.isEmpty(grandId), AcMenu::getGrandId);
        wrapper.eq(AcMenu::getPath, path);
        wrapper.eq(AcMenu::getStatus, CommonConst.YES);
        return super.selectOne(wrapper);
    }
}








