package com.bidr.agent.runtime.provider.openhands;

import com.bidr.agent.runtime.client.AgentRuntimeErrors;
import com.bidr.agent.runtime.config.AgentRuntimeConfigProvider;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;
import com.bidr.llm.agent.runtime.dto.AgentInfo;
import com.bidr.llm.agent.runtime.dto.CancelResult;
import com.bidr.llm.agent.runtime.dto.DeleteResult;
import com.bidr.llm.agent.runtime.dto.SessionInfo;
import com.bidr.llm.agent.runtime.dto.SubAgentInfo;
import com.bidr.llm.agent.runtime.dto.TurnItem;
import com.bidr.llm.agent.runtime.dto.TurnPage;
import com.bidr.llm.agent.runtime.spi.AgentRuntimeProvider;
import com.bidr.llm.agent.runtime.spi.RuntimeTurnLink;
import com.bidr.llm.agent.runtime.spi.SessionCreateCmd;
import com.bidr.llm.agent.runtime.spi.TurnOpenCmd;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.websocket.ContainerProvider;
import javax.websocket.WebSocketContainer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Title: OpenHandsProvider
 * Description: <b>OpenHands agent-server</b> 实现（REST + 会话级 socket）。
 * <p>
 * 与 agent-system 的形态差异全部关在本包内，对上仍是同一套 SPI。概念对照：
 * session ↔ conversation（{@code conversation_id} 由我方生成并指定）；agent ↔ agent profile
 * （{@code agentCode} = profile 名）；turn/{@code message_id} ↔ 该轮**用户 MessageEvent 的 id**
 * （与上游 {@code last_user_message_id} 同值）；单轮流 ↔ 会话级 socket
 * {@code /sockets/session/{cid}} 的帧按轮次绑定后过滤；挂流游标 ↔ {@code after_seq}
 * （上游 durable 帧自带 seq，天然可续传）；取消 ↔ {@code POST /interrupt}
 * （实测空闲时也返回 200，没有"终态再取消"的 409 需要容忍）。
 * <p>
 * 🔴 <b>两条实测得来的硬规矩</b>（详见 {@code docs/openhands-接入形态决策.md}）：
 * <ol>
 * <li><b>不传 {@code user_id}</b>：上游会静默丢弃（回读 null），且任意一把 session key 都能读到
 * 别的 key 建的会话——OpenHands 有"准入"没有"身份"。归属与过滤**只能**由我方
 * {@code chat_session} 表承担（{@code AgentChatSessionService.requireOwned} 先本地校验再问上游）。</li>
 * <li>{@code tags} 只作<b>可观测性冗余</b>（键必须 {@code ^[a-z0-9]+$}、值 ≤256，实测带下划线直接 422），
 * 不承担任何权限或过滤职责。</li>
 * </ol>
 * 每会话一个独立工作目录（{@code workspaceRoot/<cid>}，实测上游会自建缺失目录）：这是<b>目录级</b>隔离，
 * 不是沙箱级——同一 agent-server 内所有会话共享容器与文件权限；真要沙箱隔离得每会话一个 runtime
 * 容器，而上游 REST 面不支持 RemoteWorkspace（字段硬类型 LocalWorkspace，实测 422）。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Slf4j
public class OpenHandsProvider implements AgentRuntimeProvider {

    public static final String NAME = "openhands";

    /**
     * 上游单帧上限 4MB（full_state 快照可能很大），读缓冲留一倍余量
     */
    private static final int MAX_TEXT_BUFFER = 8 * 1024 * 1024;

    private static final int TAG_VALUE_MAX = 256;

    private final AgentRuntimeConfigProvider config;
    private final AgentRuntimeProperties properties;
    private final OpenHandsClient client;

    /**
     * 客户端模式的 WS 容器不自起后台线程，全 provider 复用一个
     */
    private final WebSocketContainer socketContainer;

    public OpenHandsProvider(AgentRuntimeConfigProvider config, AgentRuntimeProperties properties,
                             OpenHandsClient client) {
        this.config = config;
        this.properties = properties;
        this.client = client;
        this.socketContainer = ContainerProvider.getWebSocketContainer();
        this.socketContainer.setDefaultMaxTextMessageBufferSize(MAX_TEXT_BUFFER);
    }

    @Override
    public String name() {
        return NAME;
    }

    // ==================== Agent 与会话 ====================

    @Override
    public List<AgentInfo> listAgents() {
        JsonNode profiles = client.agentProfiles().path("profiles");
        if (!profiles.isArray()) {
            return Collections.emptyList();
        }
        List<AgentInfo> agents = new ArrayList<>(profiles.size());
        for (JsonNode profile : profiles) {
            AgentInfo info = new AgentInfo();
            // agentCode 用 profile 名（建会话时按名解析回 id，名字对人可读、id 对上游可读）
            info.setAgentCode(profile.path("name").asText(null));
            info.setAgentName(profile.path("name").asText(null));
            info.setDescription(profile.path("agent_kind").asText(null));
            agents.add(info);
        }
        return agents;
    }

    @Override
    public SessionInfo createSession(SessionCreateCmd cmd) {
        String conversationId = UUID.randomUUID().toString();
        String profileName = StringUtils.hasText(cmd.getAgentCode())
                ? cmd.getAgentCode() : properties.getAgentProfile();
        String profileId = client.resolveAgentProfileId(profileName);
        if (!StringUtils.hasText(profileId)) {
            throw AgentRuntimeErrors.of(-1, "OpenHands 未配置 agent profile（GET /api/agent-profiles 为空）");
        }
        Map<String, Object> workspace = new HashMap<>(2);
        workspace.put("working_dir", workspaceDir(conversationId));
        workspace.put("kind", "LocalWorkspace");

        Map<String, Object> body = new HashMap<>(6);
        body.put("conversation_id", conversationId);
        body.put("workspace", workspace);
        body.put("agent_profile_id", profileId);
        body.put("max_iterations", properties.getMaxIterations());
        Map<String, String> tags = tags(cmd);
        if (!tags.isEmpty()) {
            body.put("tags", tags);
        }
        JsonNode created = client.createConversation(body);
        log.info("OpenHands 建会话 {}（profile={}，dir={}）", created.path("id").asText(null),
                profileName, workspace.get("working_dir"));
        return toSessionInfo(created, cmd);
    }

    @Override
    public SessionInfo validateSession(String sessionId) {
        return toSessionInfo(client.conversation(sessionId), null);
    }

    @Override
    public DeleteResult deleteSession(String sessionId) {
        client.deleteConversation(sessionId);
        DeleteResult result = new DeleteResult();
        result.setSessionId(sessionId);
        result.setDeleted(Boolean.TRUE);
        return result;
    }

    /**
     * 工作目录：每会话一个（上游实测会自建缺失目录，我方无需也无法在容器里 mkdir）
     */
    private String workspaceDir(String conversationId) {
        String root = properties.getWorkspaceRoot();
        if (!StringUtils.hasText(root)) {
            root = "/workspace";
        }
        return root.endsWith("/") ? root + conversationId : root + "/" + conversationId;
    }

    /**
     * 业务标识塞进 tags（纯运维/溯源冗余）：键去掉非小写字母数字字符、值截断到 256
     */
    private Map<String, String> tags(SessionCreateCmd cmd) {
        Map<String, String> tags = new HashMap<>(4);
        putTag(tags, "businesstype", cmd.getBusinessType());
        putTag(tags, "businessid", cmd.getBusinessId());
        putTag(tags, "groupkey", cmd.getGroupKey());
        putTag(tags, "agentcode", cmd.getAgentCode());
        return tags;
    }

    private void putTag(Map<String, String> tags, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        tags.put(key, value.length() > TAG_VALUE_MAX ? value.substring(0, TAG_VALUE_MAX) : value);
    }

    private SessionInfo toSessionInfo(JsonNode conversation, SessionCreateCmd cmd) {
        SessionInfo info = new SessionInfo();
        info.setSessionId(conversation.path("id").asText(null));
        info.setName(conversation.path("title").asText(null));
        info.setCreatedAt(conversation.path("created_at").asText(null));
        info.setUpdatedAt(conversation.path("updated_at").asText(null));
        if (cmd != null) {
            info.setAgentCode(cmd.getAgentCode());
            info.setGroupKey(cmd.getGroupKey());
            info.setMetadata(cmd.getMetadata());
        }
        // cmd 为空（详情/校验路径）时 agentCode 留空：上游只记 launched_agent_profile.agent_profile_id
        // （无 profile 名），而我方本地 chat_session.agent_code 才是权威，不必为此多打一次档案查询
        return info;
    }

    // ==================== 历史与单轮 ====================

    @Override
    public TurnPage history(String sessionId, String before, Integer limit) {
        List<TurnItem> turns = OpenHandsEventCodec.toTurns(client.allEvents(sessionId));
        int size = limit == null || limit <= 0 ? 50 : Math.min(limit, 100);
        int end = turns.size();
        if (StringUtils.hasText(before)) {
            end = -1;
            for (int i = 0; i < turns.size(); i++) {
                if (before.equals(turns.get(i).getMessageId())) {
                    end = i;
                    break;
                }
            }
            if (end < 0) {
                throw AgentRuntimeErrors.of(AgentRuntimeErrors.CODE_TURN_MISSING,
                        "轮次 " + before + " 在会话 " + sessionId + " 中不存在");
            }
        }
        int from = Math.max(0, end - size);
        TurnPage page = new TurnPage();
        page.setItems(new ArrayList<>(turns.subList(from, end)));
        page.setHasMore(from > 0);
        page.setNextBefore(from > 0 ? turns.get(from).getMessageId() : null);
        return page;
    }

    @Override
    public TurnItem turn(String sessionId, String turnId) {
        for (TurnItem item : OpenHandsEventCodec.toTurns(client.allEvents(sessionId))) {
            if (turnId.equals(item.getMessageId())) {
                return item;
            }
        }
        throw AgentRuntimeErrors.of(AgentRuntimeErrors.CODE_TURN_MISSING,
                "轮次 " + turnId + " 在会话 " + sessionId + " 中不存在");
    }

    @Override
    public CancelResult cancel(String sessionId, String turnId) {
        client.interrupt(sessionId);
        CancelResult result = new CancelResult();
        result.setMessageId(turnId);
        result.setCancelled(Boolean.TRUE);
        return result;
    }

    // ==================== 流 ====================

    @Override
    public RuntimeTurnLink openTurn(TurnOpenCmd cmd) {
        OpenHandsSocket socket = connect(cmd.getSessionId());
        try {
            // 先连后发：否则可能错过本轮的用户消息事件（轮次 id 只由它给出）
            client.postMessage(cmd.getSessionId(), cmd.getContent());
        } catch (RuntimeException e) {
            socket.close();
            throw e;
        }
        return OpenHandsTurnStream.forNewTurn(socket, cmd.getSessionId(), cmd.getAgentCode(),
                cmd.getContent(), config.getIdleTimeoutMs());
    }

    @Override
    public RuntimeTurnLink attachTurn(String sessionId, String turnId) {
        // 先连直播、再读 REST 归约前缀：两个窗口重叠的部分按事件 id 去重，既不重也不漏。
        // agentCode 传 null：挂流路径不查本地会话行，而 message.started 的 agent_code 只用于展示。
        OpenHandsSocket socket = connect(sessionId);
        try {
            OpenHandsEventCodec.AttachPrefix prefix = OpenHandsEventCodec.attachPrefix(
                    client.allEvents(sessionId), sessionId, turnId, null);
            return OpenHandsTurnStream.forAttach(socket, sessionId, null, turnId, prefix,
                    config.getIdleTimeoutMs());
        } catch (RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    private OpenHandsSocket connect(String sessionId) {
        try {
            return OpenHandsSocket.open(socketContainer, client.sessionSocketUri(sessionId),
                    config.requireApiKey(), config.getIdleTimeoutMs());
        } catch (IOException e) {
            log.warn("连接 OpenHands 会话 socket 失败（{}）：{}", sessionId, e.getMessage());
            throw AgentRuntimeErrors.of(-1, "Agent 运行时流请求失败：" + e.getMessage());
        }
    }

    /**
     * 附件不走预签名直传（上游是 multipart 文件 API）
     */
    @Override
    public boolean supportsPresignedUpload() {
        return false;
    }

    /**
     * 附件走 relay 代收转推：浏览器把文件交给 relay，relay 用服务端凭据写进该会话的沙箱工作目录。
     */
    @Override
    public boolean supportsRelayUpload() {
        return true;
    }

    @Override
    public String uploadFile(String sessionId, String fileName, byte[] content) {
        String safe = safeFileName(fileName);
        String absolute = workspaceDir(sessionId) + "/" + safe;
        client.upload(sessionId, absolute, safe, content);
        return absolute;
    }

    /** 只读发现：上游有哪些可委派的子 agent 类型（供管理面展示；注册/配置不经过框架） */
    @Override
    public List<SubAgentInfo> listSubAgents() {
        List<SubAgentInfo> list = new ArrayList<>();
        JsonNode agents = client.subAgents().path("agents");
        for (JsonNode agent : agents) {
            List<String> tools = new ArrayList<>();
            for (JsonNode tool : agent.path("tools")) {
                tools.add(tool.asText());
            }
            list.add(new SubAgentInfo(
                    agent.path("name").asText(""),
                    agent.path("description").asText(""),
                    agent.path("model").asText("inherit"),
                    tools));
        }
        return list;
    }

    /**
     * 🔴 文件名清洗：只取 basename、去掉目录穿越与控制字符、限长。
     * 它会被拼进沙箱**绝对路径**交给上游写文件接口，不清洗等于把"任意路径写"开放给浏览器。
     */
    static String safeFileName(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "file";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[^A-Za-z0-9._\\-\\u4e00-\\u9fa5]", "_").replace("..", "_");
        if (name.startsWith(".")) {
            name = "_" + name.substring(1);
        }
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
