package com.bidr.socket.io.dao.repository.mysql;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.socket.io.dao.entity.ChatFriend;
import com.bidr.socket.io.dao.mapper.ChatFriendDao;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: ChatFriendService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2021/2/7 17:25
 */
@Service
public class ChatFriendService extends BaseSqlRepo<ChatFriendDao, ChatFriend> {

    public List<ChatFriend> getFriendByUserId(String userId) {
        LambdaQueryWrapper<ChatFriend> wrapper = super.getQueryWrapper();
        wrapper.eq(ChatFriend::getUserId, userId);
        wrapper.eq(ChatFriend::getConfirm, CommonConst.YES);
        return super.select(wrapper);
    }

    public List<ChatFriend> getUnconfirmedFriendByUserId(String userId) {
        LambdaQueryWrapper<ChatFriend> wrapper = super.getQueryWrapper();
        wrapper.eq(ChatFriend::getUserId, userId);
        wrapper.isNull(ChatFriend::getConfirm);
        return super.select(wrapper);
    }
}
