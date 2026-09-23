package com.bidr.agent.runtime.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bidr.agent.runtime.client.AgentRuntimeErrorCode;
import com.bidr.agent.runtime.client.AgentRuntimeErrors;
import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.dao.entity.ChatSession;
import com.bidr.agent.runtime.dao.repository.ChatSessionService;
import com.bidr.llm.agent.runtime.dto.DeleteResult;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.agent.runtime.spi.AgentRequestContext;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.SessionCreateCmd;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Title: AgentChatSessionService
 * Description: 会话映射与归属（本地表 = 业务侧会话清单的唯一来源）：
 * <ul>
 * <li><b>归属强校验</b>：平台 open 通道按 Key 可见整租户，**不提供按用户隔离**，故按用户隔离
 * 一律由本服务按 operator 强制（越权 → 权限错误；不存在/已删 → 平台码 40402 直通，前端透明新建）；</li>
 * <li><b>懒建会话</b>：上游建会话 + 本地登记（分组键、业务绑定、标题）——平台 name 创建即冻结，
 * 标题只能本地维护（D10）；</li>
 * <li>不落消息正文：平台为消息与过程的权威，避免双写与口径漂移。</li>
 * </ul>
 *
 * @author sharp
 * @since 2026/9/22
 */
@Slf4j
public class AgentChatSessionService {

    /**
     * 本地有效标记（软删位）
     */
    public static final int VALID = 1;
    public static final int INVALID = 0;

    private final ChatSessionService repository;
    private final AgentRuntimeProvider provider;
    private final AgentRequestContext context;
    private final AgentRuntimeConfigProvider config;

    public AgentChatSessionService(ChatSessionService repository,
                                   AgentRuntimeProvider provider,
                                   AgentRequestContext context,
                                   AgentRuntimeConfigProvider config) {
        this.repository = repository;
        this.provider = provider;
        this.context = context;
        this.config = config;
    }

    /**
     * 建会话：上游建 → 本地登记（归属 = 当前登录人）
     */
    public SessionInfo create(String requestedAgentCode, String title, String requestedGroupKey,
                              String businessType, String businessId) {
        if (!config.isConfigured()) {
            throw AgentRuntimeErrors.notConfigured();
        }
        context.assertBusinessReadable(businessType, businessId);

        String agentCode = context.resolveAgentCode(requestedAgentCode);
        String groupKey = context.resolveGroupKey(requestedGroupKey, businessType, businessId);
        Map<String, Object> metadata = context.resolveMetadata(businessType, businessId);

        SessionInfo session = provider.createSession(new SessionCreateCmd(
                agentCode, title, groupKey, metadata, businessType, businessId));
        String operator = context.currentOperator();

        ChatSession entity = new ChatSession();
        entity.setSessionId(session.getSessionId());
        entity.setAgentCode(agentCode);
        entity.setOperator(operator);
        entity.setGroupKey(groupKey);
        entity.setBusinessType(businessType);
        entity.setBusinessId(businessId);
        entity.setTitle(StringUtils.hasText(title) ? title : null);
        entity.setLastActiveAt(new Date());
        entity.setValid(VALID);
        repository.insert(entity);
        context.onSessionCreated(entity);
        return session;
    }

    /**
     * 归属强校验：返回本地会话行；不存在/已删 → 平台码 40402 直通；非本人 → 权限错误
     */
    public ChatSession requireOwned(String sessionId) {
        ChatSession session = repository.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getValid, VALID));
        if (session == null) {
            throw new ServiceException(new AgentRuntimeErrorCode(
                    AgentRuntimeErrors.CODE_SESSION_MISSING, "会话不存在或已删除"));
        }
        String operator = context.currentOperator();
        if (!StringUtils.hasText(operator) || !operator.equals(session.getOperator())) {
            throw new ServiceException(ErrCodeSys.SYS_PERMIT_ERROR, "该对话");
        }
        return session;
    }

    /**
     * 我的会话清单（本地表；可按业务绑定过滤）
     */
    public List<ChatSession> listMine(String businessType, String businessId) {
        return repository.select(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getOperator, context.currentOperator())
                .eq(ChatSession::getValid, VALID)
                .eq(StringUtils.hasText(businessType), ChatSession::getBusinessType, businessType)
                .eq(StringUtils.hasText(businessId), ChatSession::getBusinessId, businessId)
                .orderByDesc(ChatSession::getLastActiveAt, ChatSession::getId));
    }

    /**
     * 会话详情（恢复路径）：本地归属校验 + 上游校验（上游 40402 直通，前端透明新建）
     */
    public SessionInfo detail(String sessionId) {
        requireOwned(sessionId);
        return provider.validateSession(sessionId);
    }

    /**
     * 软删：上游软删 + 本地置无效（列表即不可见）
     */
    public DeleteResult delete(String sessionId) {
        requireOwned(sessionId);
        DeleteResult result = provider.deleteSession(sessionId);
        ChatSession patch = new ChatSession();
        patch.setSessionId(sessionId);
        patch.setValid(INVALID);
        repository.update(patch, Wrappers.<ChatSession>lambdaUpdate().eq(ChatSession::getSessionId, sessionId));
        return result;
    }

    /**
     * 活跃打点（发消息时；失败不影响主链路）：刷新活跃时间 + 消息计数自增。
     * 一次发送在会话里恒为两条消息（用户提问 + 助手回复，含错误态占位），故 +2 与列表"条消息"口径一致。
     */
    public void touch(String sessionId) {
        try {
            repository.update(null, Wrappers.<ChatSession>lambdaUpdate()
                    .eq(ChatSession::getSessionId, sessionId)
                    .set(ChatSession::getLastActiveAt, new Date())
                    .setSql("message_count = IFNULL(message_count, 0) + 2"));
        } catch (Exception e) {
            log.warn("会话活跃打点失败（{}）：{}", sessionId, e.getMessage());
        }
    }

    /**
     * 维护本地标题（平台 name 冻结，故标题只在本侧改）
     */
    public void rename(String sessionId, String title) {
        requireOwned(sessionId);
        ChatSession patch = new ChatSession();
        patch.setSessionId(sessionId);
        patch.setTitle(title);
        repository.update(patch, Wrappers.<ChatSession>lambdaUpdate().eq(ChatSession::getSessionId, sessionId));
    }
}