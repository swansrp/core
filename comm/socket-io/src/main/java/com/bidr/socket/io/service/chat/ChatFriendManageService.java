package com.bidr.socket.io.service.chat;


import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.socket.io.dao.entity.ChatFriend;
import com.bidr.socket.io.dao.repository.mysql.ChatFriendService;
import com.bidr.socket.io.vo.chat.friend.ChatFriendRes;
import com.bidr.socket.io.vo.chat.friend.ConfirmFriendReq;
import com.bidr.socket.io.vo.chat.friend.MakeFriendReq;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Title: ChatFriendManageService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/7 16:46
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.service.chat
 */
@Service
public class ChatFriendManageService {

    @Resource
    private TokenService tokenService;
    @Resource
    private AcUserService acUserService;
    @Resource
    private ChatFriendService chatFriendService;

    public List<ChatFriendRes> getFriendList() {
        String userId = tokenService.getCurrentUserId();
        List<ChatFriend> friendList = chatFriendService.getFriendByUserId(userId);
        return buildChatFriendRes(friendList);
    }

    private List<ChatFriendRes> buildChatFriendRes(List<ChatFriend> friendList) {
        List<ChatFriendRes> resList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(friendList)) {
            for (ChatFriend friend : friendList) {
                AcUser user = acUserService.getByCustomerNumber(friend.getFriendId());
                ChatFriendRes res = buildChatFriendRes(user);
                resList.add(res);
            }
        }
        return resList;
    }

    private ChatFriendRes buildChatFriendRes(AcUser user) {
        ChatFriendRes res = new ChatFriendRes();
        res.setUserId(user.getUserName());
        res.setUserName(user.getName());
        res.setAvatar(user.getAvatar());
        return res;
    }

    @Transactional(rollbackFor = Exception.class)
    public void friend(MakeFriendReq req) {
        String userId = tokenService.getCurrentUserId();
        ChatFriend chatFriend = buildChatFriend(userId, req.getFriendId());
        chatFriendService.insertOrUpdate(chatFriend);
    }

    private ChatFriend buildChatFriend(String userId, String friendId) {
        ChatFriend friend = new ChatFriend();
        friend.setUserId(userId);
        friend.setFriendId(friendId);
        return friend;
    }

    public List<ChatFriendRes> getUnconfirmedFriend() {
        String userId = tokenService.getCurrentUserId();
        List<ChatFriend> friendList = chatFriendService.getUnconfirmedFriendByUserId(userId);
        return buildChatFriendRes(friendList);
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmFriend(ConfirmFriendReq req) {
        String userId = tokenService.getCurrentUserId();
        ChatFriend chatFriend = buildChatFriend(req.getFriendId(), userId);
        chatFriend.setConfirm(req.getConfirmed());
        chatFriend.setConfirmAt(new Date());
        chatFriendService.insertOrUpdate(chatFriend);
        if (StringUtils.equals(req.getConfirmed(), CommonConst.YES)) {
            chatFriend.setUserId(userId);
            chatFriend.setFriendId(req.getFriendId());
            chatFriendService.insertOrUpdate(chatFriend);
        }
    }
}
