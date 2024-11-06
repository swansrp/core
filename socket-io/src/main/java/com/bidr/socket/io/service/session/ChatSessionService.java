package com.bidr.socket.io.service.session;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.socket.io.bo.session.ChatSession;
import com.bidr.socket.io.constant.param.ChatParam;
import com.bidr.socket.io.dao.po.chat.ChatHistory;
import com.bidr.socket.io.dao.repository.mongo.ChatHistoryRepository;
import com.bidr.socket.io.dao.repository.redis.ChatSessionRepository;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIONamespace;
import com.corundumstudio.socketio.SocketIOServer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: ChatSessionService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/9/25 18:21
 */
@Slf4j
@Service
public class ChatSessionService {

    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, ChatSession>> map = new ConcurrentHashMap<>();
    @Resource
    private SocketIOServer socketIOServer;
    @Resource
    private ChatSessionRepository chatSessionRepository;
    @Resource
    private ChatHistoryRepository chatHistoryRepository;
    @Resource
    private TokenService tokenService;
    @Resource
    private SysConfigCacheService sysConfigCacheService;

    public boolean existed(String operator) {
        if (map.get(operator) != null) {
            return MapUtils.isNotEmpty(map.get(operator));
        }
        return false;
    }

    public boolean login(String operator, TokenInfo token, UUID sessionId) {
        boolean needConnectAgent = false;
        if (!hasLogin(operator, sessionId.toString())) {
            chatSessionRepository.add(operator, sessionId.toString());
            Long clientNum = chatSessionRepository.size(operator);
            log.info("[用户登录]当前用户[{}]登录数: {}", operator, clientNum);
            tokenService.putItem(token, TokenItem.SESSION_ID.name(), sessionId.toString());
            String namespace = buildSocketIoNamespace(operator);
            ChatSession chatSession = new ChatSession(namespace, operator, sessionId);
            add(operator, chatSession);
            needConnectAgent = true;
        } else {
            log.info("[用户登录]当前用户[{}-{}]已经登录", operator, sessionId);
            tokenService.putItem(token, TokenItem.SESSION_ID.name(), sessionId);
        }
        return needConnectAgent;
    }

    public boolean hasLogin(String operator, String sessionId) {
        if (MapUtils.isNotEmpty(map.get(operator))) {
            return map.get(operator).get(sessionId) != null;
        }
        return false;
    }

    public String buildSocketIoNamespace(String operator) {
        return "/" + operator;
    }

    private synchronized void add(String operator, ChatSession session) {
        ChatHistory chatHistory = buildChatHistory(operator, session);
        chatHistory.setLoginAt(new Date());
        chatHistoryRepository.insertOrUpdateById(chatHistory);
        ConcurrentHashMap<String, ChatSession> sessionMap = map.getOrDefault(operator, new ConcurrentHashMap<>(16));
        sessionMap.put(session.getSessionId().toString(), session);
        map.put(operator, sessionMap);
    }

    private ChatHistory buildChatHistory(String operator, ChatSession session) {
        ChatHistory chatHistory = new ChatHistory();
        chatHistory.setSessionId(session.getSessionId().toString());
        chatHistory.setUserId(operator);
        int expired = sysConfigCacheService.getParamInt(ChatParam.CHAT_HISTORY_EXPIRED_TIME);
        chatHistory.setExpireTime(DateUtil.endTime(DateUtils.addSeconds(new Date(), expired)));
        return chatHistory;
    }

    public boolean hasLogin(String operator) {
        Long clientNum = chatSessionRepository.size(operator);
        log.info("当前用户[{}]登录数: {}", operator, clientNum);
        return new BigDecimal(clientNum).compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * 登出 多端用户 登出时session个数-1
     *
     * @param operator 用户id
     * @param token    token
     * @return 返回剩余客户端个数与0的关系
     */
    public boolean logoff(String operator, TokenInfo token, String sessionId) {
        remove(operator, sessionId);
        chatSessionRepository.remove(operator, sessionId);
        if (tokenService.isTokenExist(token)) {
            tokenService.removeItemByToken(token, TokenItem.SESSION_ID.name());
        }
        Long clientNum = chatSessionRepository.size(operator);
        log.info("[用户登出]当前用户[{}]连接数: {}", operator, clientNum);
        boolean needDisConnectAgent = clientNum <= 0;
        if (needDisConnectAgent) {
            chatSessionRepository.delete(operator);
        }
        return needDisConnectAgent;
    }

    private synchronized void remove(String operator, String sessionId) {
        Map<String, ChatSession> sessionMap = getSession(operator);
        if (sessionMap == null) {
            return;
        }
        ChatSession session = sessionMap.get(sessionId);
        if (session != null) {
            ChatHistory chatHistory = buildChatHistory(operator, session);
            chatHistory.setLogoffAt(new Date());
            chatHistoryRepository.updateById(chatHistory);
            sessionMap.remove(sessionId);
        }
    }

    public Map<String, ChatSession> getSession(String operator) {
        return map.get(operator);
    }

    @PreDestroy
    public void destroy() {
        log.info(JsonUtil.toJson(map));
        if (MapUtils.isNotEmpty(map)) {
            for (Map.Entry<String, ConcurrentHashMap<String, ChatSession>> entry : map.entrySet()) {
                if (MapUtils.isNotEmpty(entry.getValue())) {
                    String operator = entry.getKey();
                    kickoff(operator);
                }
            }
        }
    }

    public void kickoff(String operator) {
        Map<String, ChatSession> chatSessionMap = get(operator);
        if (MapUtils.isEmpty(chatSessionMap)) {
            log.info("用户[{}]已经退出", operator);
        } else {
            String namespace = buildSocketIoNamespace(operator);
            for (Map.Entry<String, ChatSession> entry : chatSessionMap.entrySet()) {
                SocketIOClient client = socketIOServer.getNamespace(namespace)
                        .getClient(entry.getValue().getSessionId());
                if (client != null) {
                    chatSessionRepository.remove(operator, entry.getValue().getSessionId().toString());
                    client.disconnect();
                }
            }
        }

    }

    public Map<String, ChatSession> get(String operator) {
        return map.get(operator);
    }

    public SocketIONamespace getSocketIONamespace(String operator) {
        Validator.assertNotNull(map.get(operator), ErrCodeSys.PA_DATA_NOT_EXIST, "用户连接");
        String namespace = buildSocketIoNamespace(operator);
        return socketIOServer.getNamespace(namespace);
    }
}
