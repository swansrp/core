package com.bidr.wechat.service.account;

import com.bidr.authorization.bo.account.UserPermitInfo;
import com.bidr.authorization.bo.permit.PermitInfo;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.wechat.constant.WechatMenuTypeDict;
import com.bidr.wechat.dao.entity.MmRoleTagMap;
import com.bidr.wechat.dao.repository.MmRoleTagMapService;
import com.bidr.wechat.po.platform.menu.CreateWechatPlatformConditionalMenuRes;
import com.bidr.wechat.po.platform.menu.WechatPlatformMenu;
import com.bidr.wechat.po.platform.menu.WechatPlatformMenuMatchRule;
import com.bidr.wechat.po.platform.tag.UserTag;
import com.bidr.wechat.service.menu.WechatPublicMenuService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Title: SyncAccountMenuService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/17 18:01
 */
@Service
public class SyncAccountMenuService {

    private static final String CLIENT_TYPE = "PUBLIC";

    @Resource
    private AcUserRoleMenuService acUserRoleMenuService;
    @Resource
    private MmRoleTagMapService mmRoleTagMapService;
    @Resource
    private WechatPublicMenuService wechatPublicMenuService;
    @Resource
    private SyncAccountUserTagService syncAccountUserTagService;


    @Transactional(rollbackFor = Exception.class)
    public void syncPermitAndWechatPublicMenu(Long roleId) {
        MmRoleTagMap mmRoleTagMap = mmRoleTagMapService.getOneRoleTagMapByRoleId(roleId);
        if (mmRoleTagMap != null) {
            if (StringUtils.isNotBlank(mmRoleTagMap.getMenuId())) {
                wechatPublicMenuService.deleteMenu(mmRoleTagMap.getMenuId());
            }
        } else {
            UserTag userTag = syncAccountUserTagService.syncWechatPublicUserTagByRole(roleId);
            mmRoleTagMap = buildMmRoleTagMap(roleId, userTag);
        }
        List<WechatPlatformMenu> button = buildWechatPlatformMenus(roleId);
        WechatPlatformMenuMatchRule rule = new WechatPlatformMenuMatchRule();
        rule.setTagId(mmRoleTagMap.getTagId());
        CreateWechatPlatformConditionalMenuRes res = wechatPublicMenuService.createMenu(button, rule);
        mmRoleTagMap.setMenuId(res.getMenuId());
        mmRoleTagMapService.updateById(mmRoleTagMap);
    }

    private List<WechatPlatformMenu> buildWechatPlatformMenus(Long roleId) {
        List<Long> roleIdList = new ArrayList<>();
        roleIdList.add(roleId);
        UserPermitInfo permitTree = acUserRoleMenuService.getByRoleIdListAndClientType(roleIdList, CLIENT_TYPE);
        return buildPermitTree(permitTree.getMenuList());
    }

    private MmRoleTagMap buildMmRoleTagMap(Long roleId, UserTag userTag) {
        MmRoleTagMap map = new MmRoleTagMap();
        map.setTagName(userTag.getName());
        map.setTagId(userTag.getId());
        map.setRoleId(roleId);
        return map;
    }

    private List<WechatPlatformMenu> buildPermitTree(List<PermitInfo> permitList) {
        List<WechatPlatformMenu> res = new ArrayList<>();
        List<PermitInfo> parentMenuList = permitList.stream().filter(permit -> permit.getPid() == null).collect(Collectors.toList());
        parentMenuList.forEach(parentMenu -> res.add(buildPermitTree(parentMenu, permitList)));
        return res;
    }

    private WechatPlatformMenu buildPermitTree(PermitInfo parentMenu, List<PermitInfo> permitList) {
        WechatPlatformMenu menu = buildWechatPlatformMenu(parentMenu);
        List<PermitInfo> menuList = permitList.stream().filter(permit -> parentMenu.getMenuId().equals(permit.getPid())).collect(Collectors.toList());
        for (PermitInfo subMenu : menuList) {
            menu.getSubButton().add(buildPermitTree(subMenu, permitList));
        }
        return menu;
    }

    private WechatPlatformMenu buildWechatPlatformMenu(PermitInfo parentMenu) {
        WechatPlatformMenu menu = new WechatPlatformMenu();
        menu.setSubButton(new ArrayList<>());
        menu.setName(parentMenu.getTitle());
        if (FuncUtil.equals(parentMenu.getMenuType(), WechatMenuTypeDict.VIEW.getValue())) {
            menu.setType(WechatMenuTypeDict.VIEW.getText());
            menu.setUrl(parentMenu.getPath());
        } else if (!FuncUtil.equals(parentMenu.getMenuType(), WechatMenuTypeDict.MENU.getValue())) {
            menu.setType(WechatMenuTypeDict.MENU.getText());
            menu.setKey(parentMenu.getPath());
        }
        if (FuncUtil.equals(parentMenu.getMenuType(), WechatMenuTypeDict.MEDIA.getValue()) || FuncUtil.equals(parentMenu.getMenuType(), WechatMenuTypeDict.VIEW_LIMITED.getValue())) {
            menu.setMediaId(parentMenu.getPath());
        } else if (FuncUtil.equals(parentMenu.getMenuType(), WechatMenuTypeDict.MINI.getValue())) {
            menu.setAppId(parentMenu.getPath());
            menu.setPagePath("/");
        }

        return menu;
    }
}
