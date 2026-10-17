package com.bidr.socket.io.controller;


import com.bidr.kernel.config.response.Resp;
import com.bidr.socket.io.service.chat.ChatRoomManageService;
import com.bidr.socket.io.vo.room.*;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: BaseChatRoomController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2024/10/31 11:00
 */
public class BaseChatRoomController {
    @Resource
    private ChatRoomManageService chatRoomManageService;

    @ApiOperation(value = "创建聊天室", notes = "创建聊天室")
    @RequestMapping(value = "/chat/room", method = RequestMethod.PUT)
    public UpdateChatRoomRes createRoom(@RequestBody UpdateChatRoomReq req) {
        return chatRoomManageService.create(req);
    }

    @ApiOperation(value = "获取聊天室列表", notes = "获取聊天室列表")
    @RequestMapping(value = "/chat/room", method = RequestMethod.GET)
    public List<ChatRoomRes> getRoom(ChatRoomReq req) {
        return chatRoomManageService.getRoom(req);
    }

    @ApiOperation(value = "修改聊天室", notes = "修改聊天室")
    @RequestMapping(value = "/chat/room", method = RequestMethod.POST)
    public void updateRoom(@RequestBody UpdateChatRoomReq req) {
        chatRoomManageService.update(req);
        Resp.notice("修改聊天室成功");
    }

    @ApiOperation(value = "解散聊天室", notes = "解散聊天室")
    @RequestMapping(value = "/chat/room", method = RequestMethod.DELETE)
    public void destroyRoom(@RequestBody DeleteChatRoomReq req) {
        chatRoomManageService.delete(req);
        Resp.notice("解散聊天室成功");
    }

    @ApiOperation(value = "修改聊天室成员", notes = "修改聊天室成员")
    @RequestMapping(value = "/chat/room/member", method = RequestMethod.POST)
    public void updateRoomMemberList(@RequestBody UpdateChatRoomMemberReq req) {
        chatRoomManageService.updateMember(req);
        Resp.notice("修改聊天室成员成功");
    }

}
