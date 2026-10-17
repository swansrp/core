package com.bidr.socket.io.service.chat;


import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.socket.io.constant.dict.RoomRoleDict;
import com.bidr.socket.io.dao.entity.ChatRoom;
import com.bidr.socket.io.dao.entity.ChatRoomMember;
import com.bidr.socket.io.dao.repository.mysql.ChatRoomMemberService;
import com.bidr.socket.io.dao.repository.mysql.ChatRoomService;
import com.bidr.socket.io.vo.room.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Title: ChatRoomManageService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/2/5 17:41
 * @description Project Name: Seed
 * @Package: com.srct.service.socketio.service.chat
 */
@Service
public class ChatRoomManageService {

    @Resource
    private TokenService tokenService;
    @Resource
    private AcUserService acUserService;
    @Resource
    private ChatRoomService chatRoomService;
    @Resource
    private ChatRoomMemberService chatRoomMemberService;

    @Transactional(rollbackFor = Exception.class)
    public UpdateChatRoomRes create(UpdateChatRoomReq req) {
        String userId = tokenService.getCurrentUserId();
        String roomId = UUID.randomUUID().toString();
        ChatRoom chatRoom = ReflectionUtil.copy(req, ChatRoom.class);
        chatRoom.setId(roomId);
        chatRoomService.updateById(chatRoom);
        if (CollectionUtils.isNotEmpty(req.getMemberList())) {
            for (String memberId : req.getMemberList()) {
                ChatRoomMember member = buildChatRoomMember(roomId, memberId, RoomRoleDict.MEMBER.getValue());
                chatRoomMemberService.insert(member);
            }
        }
        ChatRoomMember member = buildChatRoomMember(roomId, userId, RoomRoleDict.OWNER.getValue());
        chatRoomMemberService.insertOrUpdate(member);
        return buildUpdateChatRoomRes(chatRoom);
    }

    private ChatRoomMember buildChatRoomMember(String roomId, String memberId, String role) {
        ChatRoomMember chatRoomMember = new ChatRoomMember();
        chatRoomMember.setRoomId(roomId);
        chatRoomMember.setUserId(memberId);
        chatRoomMember.setRole(role);
        chatRoomMember.setValid(CommonConst.YES);
        return chatRoomMember;
    }

    private UpdateChatRoomRes buildUpdateChatRoomRes(ChatRoom chatRoom) {
        UpdateChatRoomRes res = new UpdateChatRoomRes();
        List<ChatRoomMember> memberList = chatRoomMemberService.getMemberListByRoomId(chatRoom.getId());
        if (CollectionUtils.isNotEmpty(memberList)) {
            for (ChatRoomMember member : memberList) {
                AcUser user = acUserService.getByCustomerNumber(member.getUserId());
                ChatMemberVO vo = buildChatMemberVO(user, member.getRole());
                res.getMemberList().add(vo);
            }
        }
        res.setRoomId(chatRoom.getId());
        res.setTitle(chatRoom.getTitle());
        return res;
    }

    private ChatMemberVO buildChatMemberVO(AcUser user, String role) {
        ChatMemberVO vo = new ChatMemberVO();
        vo.setRole(DictEnumUtil.getEnumByValue(role, RoomRoleDict.class).getLabel());
        vo.setUserId(user.getCustomerNumber());
        vo.setUserName(user.getName());
        vo.setAvatar(user.getAvatar());
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(UpdateChatRoomReq req) {
        ChatRoom chatRoom = ReflectionUtil.copy(req, ChatRoom.class);
        chatRoomService.updateById(chatRoom);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(DeleteChatRoomReq req) {
        chatRoomMemberService.deleteByRoomId(req.getRoomId());
        chatRoomService.deleteById(req.getRoomId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMember(UpdateChatRoomMemberReq req) {
        if (CollectionUtils.isNotEmpty(req.getAddMemberList())) {
            for (String memberId : req.getAddMemberList()) {
                ChatRoomMember member = buildChatRoomMember(req.getRoomId(), memberId, RoomRoleDict.MEMBER.getValue());
                chatRoomMemberService.insertOrUpdate(member);
            }
        }
        if (CollectionUtils.isNotEmpty(req.getRemoveMemberList())) {
            for (String memberId : req.getRemoveMemberList()) {
                chatRoomMemberService.deleteByRoomIdAndUserId(req.getRoomId(), memberId);
            }
        }
    }

    public List<ChatRoomRes> getRoom(ChatRoomReq req) {
        List<ChatRoomRes> resList = new ArrayList<>();
        String userId = tokenService.getCurrentUserId();
        List<ChatRoomMember> chatRoomMemberList = chatRoomMemberService.getRoomListByUserIdAndRoomId(userId,
                req.getRoomId());
        if (CollectionUtils.isNotEmpty(chatRoomMemberList)) {
            for (ChatRoomMember chatRoomMember : chatRoomMemberList) {
                ChatRoom room = chatRoomService.selectById(chatRoomMember.getRoomId());
                UpdateChatRoomRes updateChatRoomRes = buildUpdateChatRoomRes(room);
                ChatRoomRes res = ReflectionUtil.copy(updateChatRoomRes, ChatRoomRes.class);
                resList.add(res);
            }
        }
        return resList;
    }
}
