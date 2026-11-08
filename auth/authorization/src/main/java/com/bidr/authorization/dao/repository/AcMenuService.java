package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.authorization.constants.dict.MenuTypeDict;
import com.bidr.authorization.dao.entity.AcMenu;
import com.bidr.authorization.dao.mapper.AcMenuDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: AcMenuService
 * Description: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/17 10:11
 */
@Service
public class AcMenuService extends AuthzVersionedSqlRepo<AcMenuDao, AcMenu> {

    public List<AcMenu> getMainMenu() {
        return getMainMenu(null);
    }

    /**
     * 主菜单查询（方案B 棒3 §4.4）：appCode 空＝平台自身（app_code IS NULL），非空＝等值收窄。
     */
    public List<AcMenu> getMainMenu(String appCode) {
        return super.select(mainMenuWrapper(appCode));
    }

    /**
     * 主菜单查询条件（包内可见＝给离线测试直接断言 wrapper 形态，同棒2 partnerIdWrapper 纪律）。
     */
    LambdaQueryWrapper<AcMenu> mainMenuWrapper(String appCode) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper()
                .eq(AcMenu::getMenuType, MenuTypeDict.MENU.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderBy(true, true, AcMenu::getShowOrder);
        return applyAppCode(wrapper, appCode);
    }

    public List<AcMenu> getAllMenu(boolean isAdmin) {
        return getAllMenu(isAdmin, null);
    }

    /**
     * 全量菜单查询（方案B 棒3 §4.4）：appCode 空＝平台自身（app_code IS NULL），非空＝等值收窄。
     */
    public List<AcMenu> getAllMenu(boolean isAdmin, String appCode) {
        return super.select(allMenuWrapper(isAdmin, appCode));
    }

    /**
     * 全量菜单查询条件（包内可见＝给离线测试直接断言 wrapper 形态，同上）。
     */
    LambdaQueryWrapper<AcMenu> allMenuWrapper(boolean isAdmin, String appCode) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        if (!isAdmin) {
            wrapper.ne(AcMenu::getMenuId, 1);
            wrapper.or(or -> or.ne(AcMenu::getGrandId, 1).isNull(AcMenu::getGrandId));
        }
        return applyAppCode(wrapper, appCode);
    }

    public List<AcMenu> getSubMenu(Long menuId) {
        return getSubMenu(menuId, null);
    }

    /**
     * 子菜单查询（方案B 棒3 §4.4）：appCode 空＝平台自身（app_code IS NULL），非空＝等值收窄。
     */
    public List<AcMenu> getSubMenu(Long menuId, String appCode) {
        return super.select(subMenuWrapper(menuId, appCode));
    }

    /**
     * 子菜单查询条件（包内可见＝给离线测试直接断言 wrapper 形态，同上）。
     */
    LambdaQueryWrapper<AcMenu> subMenuWrapper(Long menuId, String appCode) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getGrandId, menuId)
                .in(AcMenu::getMenuType, MenuTypeDict.SUB_MENU.getValue(), MenuTypeDict.BUTTON.getValue())
                .eq(AcMenu::getStatus, CommonConst.YES).eq(AcMenu::getVisible, CommonConst.YES)
                .orderByAsc(AcMenu::getShowOrder);
        return applyAppCode(wrapper, appCode);
    }

    public List<AcMenu> getContent(Long menuId) {
        return getContent(menuId, null);
    }

    /**
     * 目录查询（方案B 棒3 §4.4）：appCode 空＝平台自身（app_code IS NULL），非空＝等值收窄。
     */
    public List<AcMenu> getContent(Long menuId, String appCode) {
        return super.select(contentWrapper(menuId, appCode));
    }

    /**
     * 目录查询条件（包内可见＝给离线测试直接断言 wrapper 形态，同上）。
     */
    LambdaQueryWrapper<AcMenu> contentWrapper(Long menuId, String appCode) {
        LambdaQueryWrapper<AcMenu> wrapper = super.getQueryWrapper().eq(AcMenu::getPid, menuId)
                .eq(AcMenu::getMenuType, MenuTypeDict.CONTENT.getValue()).eq(AcMenu::getStatus, CommonConst.YES)
                .eq(AcMenu::getVisible, CommonConst.YES).orderByAsc(AcMenu::getShowOrder);
        return applyAppCode(wrapper, appCode);
    }

    /**
     * 归属应用谓词（方案B 棒3 §4.4）：appCode 空 ⇒ {@code app_code IS NULL}（平台自身存量行为），
     * 非空 ⇒ {@code app_code = ?}。包内可见＝给离线测试直接断言 wrapper 形态
     * （同 {@link AcSubsystemService#menuAppCodeWrapper} 纪律），不为测试拓宽公开 API。
     */
    LambdaQueryWrapper<AcMenu> applyAppCode(LambdaQueryWrapper<AcMenu> wrapper, String appCode) {
        if (FuncUtil.isEmpty(appCode)) {
            wrapper.isNull(AcMenu::getAppCode);
        } else {
            wrapper.eq(AcMenu::getAppCode, appCode);
        }
        return wrapper;
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








