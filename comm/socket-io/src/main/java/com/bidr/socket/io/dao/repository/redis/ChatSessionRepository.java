package com.bidr.socket.io.dao.repository.redis;

import com.bidr.platform.redis.repository.BaseRedisSetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import static com.bidr.socket.io.dao.po.key.SocketIoRedisKey.SOCKET_IO_SESSION_USER_LIST;

/**
 * Title: ChatSession
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/9/27 16:47
 */
@Slf4j
@Repository
public class ChatSessionRepository extends BaseRedisSetRepository {
    @Override
    public Long remove(String key, Object value) {
        log.info("删除客户登录信息 {} {}", key, value);
        return redisService.setRemove(getBaseKey() + key, value);
    }

    @Override
    protected String getBaseKey() {
        return SOCKET_IO_SESSION_USER_LIST;
    }
}
