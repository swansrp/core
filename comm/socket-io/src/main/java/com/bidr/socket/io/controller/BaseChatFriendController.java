package com.bidr.socket.io.controller;


import com.bidr.kernel.config.response.Resp;
import com.bidr.socket.io.service.chat.ChatFriendManageService;
import com.bidr.socket.io.vo.chat.friend.ChatFriendRes;
import com.bidr.socket.io.vo.chat.friend.ConfirmFriendReq;
import com.bidr.socket.io.vo.chat.friend.MakeFriendReq;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: BaseChatFriendController
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @author Sharp
 * @since 2024/10/31 11:00
 */
public class BaseChatFriendController {

    @Resource
    private ChatFriendManageService chatFriendManageService;

    @ApiOperation(value = "获取好友列表", notes = "获取好友列表")
    @RequestMapping(value = "/chat/friend", method = RequestMethod.GET)
    public List<ChatFriendRes> getFriend() {
        return chatFriendManageService.getFriendList();
    }

    @ApiOperation(value = "申请添加好友", notes = "申请添加好友")
    @RequestMapping(value = "/chat/friend", method = RequestMethod.POST)
    public void makeFriend(@RequestBody MakeFriendReq req) {
        chatFriendManageService.friend(req);
        Resp.notice("申请添加好友成功");
    }

    @ApiOperation(value = "获取添加好友申请列表", notes = "获取添加好友申请列表")
    @RequestMapping(value = "/chat/friend/confirm", method = RequestMethod.GET)
    public List<ChatFriendRes> getConfirmedFriend() {
        return chatFriendManageService.getUnconfirmedFriend();
    }

    @ApiOperation(value = "审核添加好友", notes = "审核添加好友")
    @RequestMapping(value = "/chat/friend/confirm", method = RequestMethod.POST)
    public void confirmFriend(@RequestBody ConfirmFriendReq req) {
        chatFriendManageService.confirmFriend(req);
        Resp.notice("审核添加好友成功");
    }


}
