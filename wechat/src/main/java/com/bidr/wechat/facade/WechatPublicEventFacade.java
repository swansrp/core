package com.bidr.wechat.facade;

import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.wechat.constant.WechatParamConst;
import com.bidr.wechat.po.platform.msg.ReceiveUserMsg;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: WechatPublicEventFacade
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 21:19
 */
@Service
public class WechatPublicEventFacade {

    @Resource
    private WechatPublicTextFacade wechatPublicTextFacade;
    @Resource
    private SysConfigCacheService sysConfigCacheService;

    public String unsubscribe(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "欢迎您再来");
    }

    public String subscribe(ReceiveUserMsg receiveUserMsg) {
        String systemName = sysConfigCacheService.getSysConfigValue(WechatParamConst.WECHAT_SYSTEM_NAME);
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "欢迎来到" + systemName);
    }

    public String click(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "您点击的是:" + receiveUserMsg.getEventKey());
    }

    public String scancodeWaitmsg(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg,
                "您的扫码识别的url是:" + receiveUserMsg.getScanCodeInfo().getScanResult());
    }

    public String view(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "公众号不回显");
    }

    public String locationSelect(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "公众号不回显");
    }

    public String scancodePush(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "公众号不回显");
    }

    public String picSysphoto(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "公众号不回显");
    }

    public String picPhotoOrAlbum(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "公众号不回显");
    }

    public String picWeixin(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "公众号不回显");
    }
}
