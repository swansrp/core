package com.bidr.wechat.facade;

import com.bidr.wechat.po.platform.msg.ReceiveUserMsg;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: WechatPublicLocationFacade
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 21:23
 * @description Project Name: Seed
 * @Package: com.srct.service.wechat.facade
 */
@Service
public class WechatPublicLocationFacade {
    @Resource
    private WechatPublicTextFacade wechatPublicTextFacade;

    public String handle(ReceiveUserMsg receiveUserMsg) {
        return wechatPublicTextFacade.buildReplyTextMsg(receiveUserMsg, "接收到您的位置信息");
    }
}
