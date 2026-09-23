package com.bidr.agent.runtime.dao.repository;

import com.bidr.agent.runtime.dao.entity.ChatSession;
import com.bidr.agent.runtime.dao.mapper.ChatSessionMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * AI对话会话映射（业务归属与业务绑定）数据仓库（仅业务查询方法，DDL 见 Schema）
 *
 * @author sharp
 */
@Service
public class ChatSessionService extends BaseSqlRepo<ChatSessionMapper, ChatSession> {
}
