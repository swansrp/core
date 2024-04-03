package com.bidr.wechat.service.template;

import com.bidr.wechat.constant.WechatUrlConst;
import com.bidr.wechat.po.platform.template.WechatMsgTemplate;
import com.bidr.wechat.po.platform.template.WechatSendMsgTemplateRes;
import com.bidr.wechat.service.WechatPublicService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: WechatMsgTemplateService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/5/4 17:36
 */
@Service
public class WechatMsgTemplateService {

    @Resource
    private WechatPublicService wechatPublicService;

    public WechatSendMsgTemplateRes pushMsgTemplate(WechatMsgTemplate msgTemplate) {
        return wechatPublicService.post(WechatUrlConst.WECHAT_PUBLIC_MSG_TEMPLATE_POST_URL, WechatSendMsgTemplateRes.class, msgTemplate);
    }
}
