package com.bidr.socket.io.dao.repository.mysql;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.socket.io.constant.dict.RoomRoleDict;
import com.bidr.socket.io.dao.entity.ChatRoomMember;
import com.bidr.socket.io.dao.mapper.ChatRoomMemberDao;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: ChatRoomMemberService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/2/5 17:07
 */
@Service
public class ChatRoomMemberService extends BaseSqlRepo<ChatRoomMemberDao, ChatRoomMember> {

    @Resource
    private ChatRoomMemberDao chatRoomMemberDao;

    @Cacheable
    public List<ChatRoomMember> getMemberListByRoomId(String roomId) {
        LambdaQueryWrapper<ChatRoomMember> wrapper = super.getQueryWrapper();
        wrapper.eq(ChatRoomMember::getRoomId, roomId);
        wrapper.eq(ChatRoomMember::getValid, CommonConst.YES);
        return super.select(wrapper);
    }

    public List<ChatRoomMember> getRoomListByUserId(String userId) {
        LambdaQueryWrapper<ChatRoomMember> wrapper = super.getQueryWrapper();
        wrapper.eq(ChatRoomMember::getUserId, userId);
        wrapper.eq(ChatRoomMember::getValid, CommonConst.YES);
        return super.select(wrapper);
    }

    public List<ChatRoomMember> getRoomListByUserIdAndRoomId(String userId, String roomId) {
        LambdaQueryWrapper<ChatRoomMember> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(roomId), ChatRoomMember::getRoomId, roomId);
        wrapper.eq(ChatRoomMember::getUserId, userId);
        wrapper.eq(ChatRoomMember::getValid, CommonConst.YES);
        return super.select(wrapper);
    }

    public void deleteByRoomId(String roomId) {
        LambdaQueryWrapper<ChatRoomMember> wrapper = super.getQueryWrapper();
        wrapper.eq(ChatRoomMember::getRoomId, roomId);
        wrapper.eq(ChatRoomMember::getValid, CommonConst.YES);
        super.delete(wrapper);
    }

    public void deleteByRoomIdAndUserId(String roomId, String userId) {
        LambdaQueryWrapper<ChatRoomMember> wrapper = super.getQueryWrapper();
        wrapper.eq(ChatRoomMember::getRoomId, roomId);
        wrapper.eq(ChatRoomMember::getUserId, userId);
        wrapper.ne(ChatRoomMember::getRole, RoomRoleDict.OWNER.getValue());
        wrapper.eq(ChatRoomMember::getValid, CommonConst.YES);
        super.delete(wrapper);
    }
}
