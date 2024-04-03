package com.bidr.wechat.service.menu;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.wechat.constant.WechatErrorCodeConst;
import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.WechatBaseRes;
import com.bidr.wechat.po.platform.menu.*;
import com.bidr.wechat.service.WechatPublicService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: WechatPublicMenuService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/5/5 14:22
 */
@Service
public class WechatPublicMenuService {


    @Resource
    private WechatPublicService wechatPublicService;

    public void createMenu(List<WechatPlatformMenu> button) {
        CreateWechatPlatformMenuReq req = new CreateWechatPlatformMenuReq();
        req.setButton(button);
        wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_CREATE_MENU_POST_URL, WechatBaseRes.class, req);
    }

    public void deleteMenu(String menuId) {
        if (StringUtils.isBlank(menuId)) {
            wechatPublicService.get(WechatUrlConst.WECHAT_PUBLIC_DELETE_MENU_GET_URL, WechatBaseRes.class);
        } else {
            DeleteConditionalMenuReq req = new DeleteConditionalMenuReq();
            req.setMenuId(menuId);
            wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_DELETE_CONDITIONAL_MENU_POST_URL, WechatBaseRes.class, req);
        }
    }

    public CreateWechatPlatformConditionalMenuRes createMenu(List<WechatPlatformMenu> button, WechatPlatformMenuMatchRule menuMatchRule) {
        CreateWechatPlatformConditionalMenuReq req = new CreateWechatPlatformConditionalMenuReq();
        req.setButton(button);
        req.setMatchRule(menuMatchRule);
        CreateWechatPlatformConditionalMenuRes res = null;
        try {
            res = wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_CREATE_CONDITIONAL_MENU_POST_URL, CreateWechatPlatformConditionalMenuRes.class, req);
        } catch (ServiceException e) {
            if (e.getErrObj().equals(WechatErrorCodeConst.NO_SELF_MENU)) {
                CreateWechatPlatformMenuReq defaultMenuReq = new CreateWechatPlatformMenuReq();
                defaultMenuReq.setButton(button);
                wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_CREATE_MENU_POST_URL, WechatBaseRes.class, defaultMenuReq);
            }
            res = wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_CREATE_CONDITIONAL_MENU_POST_URL, CreateWechatPlatformConditionalMenuRes.class, req);
        }
        return res;
    }
}
