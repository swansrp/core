package com.bidr.wechat.facade;

import com.bidr.platform.utils.file.FreeMarkerUtil;
import com.bidr.wechat.po.platform.msg.ReceiveUserMsg;
import com.bidr.wechat.po.platform.msg.ReplyTextMsg;
import com.bidr.kernel.utils.ReflectionUtil;
import org.springframework.stereotype.Service;

/**
 * Title: WechatPublicTextFacade
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/5/4 21:19
 * @description Project Name: Seed
 * @Package: com.srct.service.wechat.facade
 */
@Service
public class WechatPublicTextFacade {
    public String handle(ReceiveUserMsg receiveUserMsg) {
        return buildReplyTextMsg(receiveUserMsg, "抱歉我不是很明白您的意思,您能说的再详细一些么");
    }

    public String buildReplyTextMsg(ReceiveUserMsg receiveUserMsg, String content) {
        ReplyTextMsg msg = new ReplyTextMsg();
        msg.setTimeStamp(String.valueOf(System.currentTimeMillis()));
        msg.setToUser(receiveUserMsg.getFromUserName());
        msg.setContent(content);
        msg.setFromUser(receiveUserMsg.getToUserName());
        return FreeMarkerUtil.freemarkerRender(ReflectionUtil.getHashMap(msg), "reply_msg.ftl");
    }

    public String handleUnknownMsgTypeHandle(ReceiveUserMsg receiveUserMsg) {
        return buildReplyTextMsg(receiveUserMsg, "抱歉我还不没有学会处理这个类型的消息呢 ^_^");
    }
}
