package com.bidr.socket.io.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.socket.io.bo.message.ChatMessage;
import com.bidr.socket.io.service.chat.ChatMessageHistoryService;
import com.bidr.socket.io.service.chat.ChatService;
import com.bidr.socket.io.vo.chat.history.ChatDeliveredReq;
import com.bidr.socket.io.vo.chat.history.ChatHistoryReq;
import com.bidr.socket.io.vo.chat.msg.ChatMessageRes;
import com.bidr.socket.io.vo.chat.msg.ChatReq;
import com.bidr.socket.io.vo.chat.msg.ChatRes;
import com.bidr.socket.io.vo.chat.msg.RetractChatReq;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: BaseChatController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */

public class BaseChatController {

    @Resource
    private ChatService chatService;
    @Resource
    private ChatMessageHistoryService chatMessageHistoryService;


    @ApiOperation(value = "发消息", notes = "发消息")
    @RequestMapping(value = "/chat", method = RequestMethod.POST)
    public ChatRes chat(@RequestBody ChatReq req) {
        return chatService.chat(req);
    }

    @ApiOperation(value = "消息回执", notes = "消息回执")
    @RequestMapping(value = "/feedback", method = RequestMethod.POST)
    public void feedback(String msgId) {
        chatService.feedback(msgId);
        Resp.notice("发送消息回执成功");
    }

    @ApiOperation(value = "撤回消息", notes = "撤回消息")
    @RequestMapping(value = "/chat/retract", method = RequestMethod.POST)
    public void retract(@RequestBody RetractChatReq req) {
        chatService.retract(req);
        Resp.notice("撤回消息成功");
    }

    @ApiOperation(value = "历史记录", notes = "获取历史记录")
    @RequestMapping(value = "/chat/history", method = RequestMethod.POST)
    public Page<ChatMessage> history(@RequestBody ChatHistoryReq req) {
        return chatMessageHistoryService.queryHistory(req);
    }

    @ApiOperation(value = "获取未读记录", notes = "获取未读记录")
    @RequestMapping(value = "/chat/history", method = RequestMethod.GET)
    public List<ChatMessageRes> unDeliver(ChatDeliveredReq req) {
        return chatMessageHistoryService.getUnDeliveredChatMessage(req);
    }
}
