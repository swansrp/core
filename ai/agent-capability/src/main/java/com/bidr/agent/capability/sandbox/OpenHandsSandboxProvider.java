package com.bidr.agent.capability.sandbox;

import com.bidr.agent.runtime.client.AgentRuntimeErrorCode;
import com.bidr.agent.runtime.client.AgentRuntimeErrors;
import com.bidr.agent.runtime.config.AgentRuntimeProperties;
import com.bidr.agent.runtime.provider.openhands.OpenHandsClient;
import com.bidr.agent.runtime.provider.openhands.OpenHandsProvider;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.agent.sandbox.AgentSandbox;
import com.bidr.llm.agent.sandbox.SandboxExecResult;
import com.bidr.llm.agent.sandbox.SandboxSeedFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: OpenHandsSandboxProvider
 * Description: {@link AgentSandbox} 的 OpenHands 实现（棒3b）——直接复用引擎 B 会话当沙箱载体：
 * OpenHands 每会话一个独立工作目录（{@code workspaceRoot/<sessionId>}），bash 端点按会话挂载
 * （{@code /api/conversations/{cid}/bash/...}，命令默认 cwd＝该会话工作区），目录级隔离天然成立，
 * 无需另建沙箱实例。<b>会话 id 口径＝引擎 B sessionId</b>（即上游 conversation id；与 relay 的
 * chat_session id 空间不撞）。
 * <p>
 * 依赖分工：{@link OpenHandsClient} 只走 bash 同步端点（{@link #exec}）；{@link OpenHandsProvider}
 * 承担会话校验/重建、种子上传（{@code uploadFile} 内含 safeRelPath 穿越防护，不在此重写）、产物回读
 * （{@code downloadFile}）与销毁。四方法均为幂等或可重入语义：
 * <ul>
 *     <li>{@link #ensure}：validate 成功只补种子；会话被上游回收（404）时按 {@code OpenHandsProvider.
 *     createSession} 同口径兜底重建<b>同 id 会话</b>（上游 {@code start_conversation} 支持 caller 指定
 *     {@code conversation_id}，已存在即复用——1.62.0 wheel 核对）。种子重复上传＝覆盖（上游 file/upload
 *     语义），无需逐个比对；</li>
 *     <li>{@link #destroy}：404 静默（幂等）。</li>
 * </ul>
 * 会话生命周期：当前由工具层幂等 {@code ensure} 兜底复用；{@code destroy} 预留管理面/收口调用
 * （llm 侧会话 store 的销毁钩子接线属后续优化，本棒不自动挂钩）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class OpenHandsSandboxProvider implements AgentSandbox {

    /**
     * exec 输出合并后的截断上限（UTF-8 字节）：防超长输出（如 cat 大文件）撑爆模型上下文；
     * 截断时在尾部追加标记让模型知道输出不完整。
     */
    static final int OUTPUT_MAX_BYTES = 64 * 1024;

    /** 截断标记（不计入上限，追加在尾部） */
    static final String TRUNCATION_NOTE = "\n…[沙箱输出超长，已截断]";

    private final OpenHandsClient client;
    private final OpenHandsProvider provider;
    private final AgentRuntimeProperties properties;

    public OpenHandsSandboxProvider(OpenHandsClient client, OpenHandsProvider provider,
                                    AgentRuntimeProperties properties) {
        this.client = client;
        this.provider = provider;
        this.properties = properties;
    }

    /**
     * 懒建会话＋预置种子。正常路径会话已由引擎建好（工具调用发生在会话进行中），validate 直接过、
     * 只补种子；会话缺失（上游回收/重启丢会话）才兜底重建——重建必须用<b>同 id</b>：exec/fetch 都按
     * 引擎 sessionId 寻址，换 id 会让后续调用 404。provider.createSession 自生成随机 conversation_id
     * 无法沿用传入标识，故按其既有口径（agent_profile_id 解析、LocalWorkspace、max_iterations）自建。
     */
    @Override
    public void ensure(String sessionId, List<SandboxSeedFile> seeds) {
        try {
            provider.validateSession(sessionId);
        } catch (ServiceException e) {
            if (!isPlatformCode(e, AgentRuntimeErrors.CODE_SESSION_MISSING)) {
                // 不可达/密钥被拒等：原样上抛——不是"会话缺失"，误建新会话只会掩盖故障
                throw e;
            }
            createConversation(sessionId);
        }
        if (seeds == null || seeds.isEmpty()) {
            return;
        }
        for (SandboxSeedFile seed : seeds) {
            // 穿越防护在 provider.uploadFile 的 safeRelPath；重复 ensure＝覆盖上传（幂等）
            provider.uploadFile(sessionId, seed.relPath(), seed.content());
        }
        log.info("OpenHands 沙箱种子预置完成: cid={} files={}", sessionId, seeds.size());
    }

    /** 按会话 id 兜底重建会话（body 口径与 OpenHandsProvider.createSession 一致，仅 conversation_id 用传入值） */
    private void createConversation(String sessionId) {
        String profileName = properties.getAgentProfile();
        String profileId = client.resolveAgentProfileId(profileName);
        if (!StringUtils.hasText(profileId)) {
            throw AgentRuntimeErrors.of(-1, "OpenHands 未配置 agent profile（GET /api/agent-profiles 为空）");
        }
        Map<String, Object> workspace = new HashMap<>(2);
        workspace.put("working_dir", workspaceDir(sessionId));
        workspace.put("kind", "LocalWorkspace");
        Map<String, Object> body = new HashMap<>(6);
        body.put("conversation_id", sessionId);
        body.put("workspace", workspace);
        body.put("agent_profile_id", profileId);
        body.put("max_iterations", properties.getMaxIterations());
        client.createConversation(body);
        log.info("OpenHands 沙箱会话兜底重建: cid={} dir={} profile={}",
                sessionId, workspace.get("working_dir"), profileName);
    }

    /** 工作目录＝workspaceRoot/&lt;sessionId&gt;（与 OpenHandsProvider.workspaceDir 同口径；彼方法私有不可复用） */
    private String workspaceDir(String sessionId) {
        String root = properties.getWorkspaceRoot();
        if (!StringUtils.hasText(root)) {
            root = "/workspace";
        }
        return root.endsWith("/") ? root + sessionId : root + "/" + sessionId;
    }

    @Override
    public SandboxExecResult exec(String sessionId, String command, long timeoutMs) {
        OpenHandsClient.BashExecResult result = client.executeBash(sessionId, command, timeoutMs);
        return new SandboxExecResult(result.exitCode(), mergeOutput(result), result.timedOut());
    }

    @Override
    public byte[] fetch(String sessionId, String relPath) {
        try {
            return provider.downloadFile(sessionId, relPath);
        } catch (ServiceException e) {
            if (isPlatformCode(e, 40010)) {
                // 端口契约：非法路径抛 IllegalArgumentException（工具层转「拒绝：…」人话给模型自行纠正）
                throw new IllegalArgumentException(stripCodePrefix(e.getMessage()));
            }
            throw e;
        }
    }

    @Override
    public void destroy(String sessionId) {
        try {
            provider.deleteSession(sessionId);
        } catch (ServiceException e) {
            if (isPlatformCode(e, AgentRuntimeErrors.CODE_SESSION_MISSING)) {
                // 幂等：已销毁/不存在静默
                return;
            }
            throw e;
        }
    }

    /** stdout/stderr 合并（stdout 在前、stderr 换行接后）并按 64KB UTF-8 字节截断 */
    static String mergeOutput(OpenHandsClient.BashExecResult result) {
        String stdout = result.stdout();
        String stderr = result.stderr();
        boolean hasOut = stdout != null && !stdout.isEmpty();
        boolean hasErr = stderr != null && !stderr.isEmpty();
        if (!hasOut && !hasErr) {
            return "";
        }
        StringBuilder merged = new StringBuilder(
                (hasOut ? stdout.length() : 0) + (hasErr ? stderr.length() : 0) + 1);
        if (hasOut) {
            merged.append(stdout);
        }
        if (hasErr) {
            if (merged.length() > 0) {
                merged.append('\n');
            }
            merged.append(stderr);
        }
        return truncateUtf8(merged.toString());
    }

    /**
     * 按 UTF-8 字节预算截断：切点若劈在多字节字符中间会产出非法 UTF-8，退刀（最多 3 字节）
     * 到字符边界再解；超限追加截断标记。
     */
    static String truncateUtf8(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= OUTPUT_MAX_BYTES) {
            return text;
        }
        for (int cut = OUTPUT_MAX_BYTES; cut > OUTPUT_MAX_BYTES - 4 && cut > 0; cut--) {
            try {
                String head = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes, 0, cut)).toString();
                return head + TRUNCATION_NOTE;
            } catch (CharacterCodingException e) {
                // 切点劈了多字节字符：退一字节再试
            }
        }
        return TRUNCATION_NOTE;
    }

    /** 判定 ServiceException 是否携带指定平台码（经 AgentRuntimeErrorCode 直通的那类） */
    private static boolean isPlatformCode(ServiceException e, int code) {
        return e != null && e.getErrCode() instanceof AgentRuntimeErrorCode
                && Integer.valueOf(code).equals(e.getErrCode().getErrCode());
    }

    /** 去掉平台码前缀（AgentRuntimeErrors.of 会加「[40010] 」前缀，工具层人话不需要它） */
    private static String stripCodePrefix(String message) {
        if (message == null) {
            return "沙箱路径非法";
        }
        if (message.startsWith("[") ) {
            int close = message.indexOf("] ");
            if (close >= 0) {
                return message.substring(close + 2);
            }
        }
        return message;
    }
}
