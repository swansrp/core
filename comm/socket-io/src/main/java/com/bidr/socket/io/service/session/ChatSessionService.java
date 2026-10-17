package com.bidr.socket.io.service.session;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.socket.io.bo.session.ChatSession;
import com.bidr.socket.io.constant.param.ChatParam;
import com.bidr.socket.io.dao.po.chat.ChatHistory;
import com.bidr.socket.io.dao.repository.mongo.ChatHistoryRepository;
import com.bidr.socket.io.dao.repository.redis.ChatSessionRepository;
import com.bidr.socket.io.utils.ClientUtil;
import com.corundumstudio.socketio.SocketIOClient;
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

    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, ChatSession>> MAP = new ConcurrentHashMap<>();
    @Resource
    private ChatSessionRepository chatSessionRepository;
    @Resource
    private ChatHistoryRepository chatHistoryRepository;
    @Resource
    private TokenService tokenService;
    @Resource
    private SysConfigCacheService sysConfigCacheService;
    @Resource
    private SocketIOServer socketioServer;

    public boolean existed(String operator) {
        if (MAP.get(operator) != null) {
            return MapUtils.isNotEmpty(MAP.get(operator));
        }
        return false;
    }

    public void login(SocketIOClient client) {
        String operator = ClientUtil.get(client, TokenItem.OPERATOR);
        UUID sessionId = client.getSessionId();
        String token = ClientUtil.get(client, TokenItem.TOKEN);
        if (!hasLogin(operator, sessionId)) {
            chatSessionRepository.add(operator, sessionId);
            Long clientNum = chatSessionRepository.size(operator);
            log.info("[用户登录]当前用户[{}]登录数: {}", operator, clientNum);
            tokenService.putItem(AuthTokenUtil.resolveToken(token), TokenItem.SESSION_ID.name(), sessionId);
            ChatSession chatSession = new ChatSession(operator, sessionId);
            add(operator, chatSession);
        } else {
            log.info("[用户登录]当前用户[{}-{}]已经登录", operator, sessionId);
            tokenService.putItem(AuthTokenUtil.resolveToken(token), TokenItem.SESSION_ID.name(), sessionId);
        }
        client.joinRoom(operator);
    }

    public boolean hasLogin(String operator, UUID sessionId) {
        if (MapUtils.isNotEmpty(MAP.get(operator))) {
            return MAP.get(operator).get(sessionId.toString()) != null;
        }
        return false;
    }

    private synchronized void add(String operator, ChatSession session) {
        ChatHistory chatHistory = buildChatHistory(operator, session);
        chatHistory.setLoginAt(new Date());
        chatHistoryRepository.insertOrUpdateById(chatHistory);
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
     * @param client 客户端
     * @return 返回剩余客户端个数与0的关系
     */
    public boolean logoff(SocketIOClient client) {
        String operator = ClientUtil.get(client, TokenItem.OPERATOR);
        UUID sessionId = client.getSessionId();
        String token = ClientUtil.get(client, TokenItem.TOKEN);
        TokenInfo tokenInfo = AuthTokenUtil.resolveToken(token);
        remove(operator, sessionId.toString());
        chatSessionRepository.remove(operator, sessionId);
        if (tokenService.isTokenExist(tokenInfo)) {
            tokenService.removeItemByToken(tokenInfo, TokenItem.SESSION_ID.name());
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
        return MAP.get(operator);
    }

    @PreDestroy
    public void destroy() {
        log.info(JsonUtil.toJson(MAP));
        if (MapUtils.isNotEmpty(MAP)) {
            for (Map.Entry<String, ConcurrentHashMap<String, ChatSession>> entry : MAP.entrySet()) {
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
            for (SocketIOClient client : socketioServer.getRoomOperations(operator).getClients()) {
                ChatSession chatSession = chatSessionMap.get(client.getSessionId().toString());
                if (chatSession != null) {
                    chatSessionRepository.remove(operator, chatSession.getSessionId().toString());
                } else {
                    log.error("sessionMap中没有找到 {}", client.getSessionId().toString());
                }
                client.disconnect();
            }
        }

    }

    public Map<String, ChatSession> get(String operator) {
        return MAP.get(operator);
    }
}
