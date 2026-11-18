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
 * chat_session id 空间不撞）。<b>🔴 本实现是共享 runtime 文件系统：{@link #put} 只保证目录级落点，
 * 不提供跨会话安全隔离</b>——同一 agent-server 内所有会话共享容器与文件权限。
 * <p>
 * 依赖分工：{@link OpenHandsClient} 只走 bash 同步端点（{@link #exec}）；{@link OpenHandsProvider}
 * 承担会话校验/重建、种子上传（{@code uploadFile} 内含 safeRelPath 穿越防护，不在此重写）、产物回读
 * （{@code downloadFile}）与销毁。六个方法（ensure/exec/fetch/put/putIfAlive/destroy）的幂等性分层：
 * {@code ensure}/{@code destroy}/{@code fetch} 幂等；{@code putIfAlive} 同路径覆盖幂等；
 * {@code put} 同名自动改名（每次调用落新落点，**非幂等**）；{@code exec} 语义由命令决定（天然可非幂等）：
 * <ul>
 *     <li>{@link #ensure}：validate 成功只补种子；会话被上游回收（404）时按 {@code OpenHandsProvider.
 *     createSession} 同口径兜底重建<b>同 id 会话</b>（上游 {@code start_conversation} 支持 caller 指定
 *     {@code conversation_id}，已存在即复用——1.62.0 wheel 核对）。种子重复上传＝覆盖（上游 file/upload
 *     语义），无需逐个比对；</li>
 *     <li>{@link #put}：目标已存在时同目录自动改名（{@code -2}…），返回实际落点；</li>
 *     <li>{@link #putIfAlive}：仅当会话已存在时按同路径覆盖写最新字节（不存在返回 false，不创建会话）；</li>
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

    /** 同名改名探测上限（{@code -2}…{@code -101} 共 100 次；超限抛固定人话 IAE） */
    private static final int MAX_RENAMES = 100;

    /** 存在性检查 exec 超时（毫秒）：{@code test -e} 正常毫秒级，纯上限 */
    private static final long EXISTENCE_CHECK_TIMEOUT_MS = 10_000L;

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

    /**
     * 同目录找可用落点：目标已占用 ⇒ {@code <base>-2.<ext>}、{@code -3}…（探测上限
     * {@link #MAX_RENAMES} 次）；无扩展名时 {@code a} → {@code a-2}；超限抛固定人话 IAE。
     */
    private String nextAvailableRelPath(String sessionId, String safeRel) {
        if (!exists(sessionId, safeRel)) {
            return safeRel;
        }
        int slash = safeRel.lastIndexOf('/');
        String dir = slash < 0 ? "" : safeRel.substring(0, slash + 1);
        String name = slash < 0 ? safeRel : safeRel.substring(slash + 1);
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        for (int attempt = 1; attempt <= MAX_RENAMES; attempt++) {
            String candidate = dir + base + "-" + (attempt + 1) + ext;
            if (!exists(sessionId, candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("同名文件过多（100 次），请指定其他 path");
    }

    /**
     * 沙箱内存在性检查（统一 {@code exec test -e}，传工作区绝对路径不依赖 cwd）：exit 0＝存在、
     * 1＝不存在、超时/其它退出码＝检查失败（ServiceException 交工具层「稍后重试」，不误判）。
     */
    private boolean exists(String sessionId, String relPath) {
        String command = "test -e " + shellQuote(workspaceDir(sessionId) + "/" + relPath);
        SandboxExecResult result = exec(sessionId, command, EXISTENCE_CHECK_TIMEOUT_MS);
        if (result.timedOut()) {
            throw new ServiceException("沙箱存在性检查失败，请稍后重试");
        }
        if (result.exitCode() == 0) {
            return true;
        }
        if (result.exitCode() == 1) {
            return false;
        }
        throw new ServiceException("沙箱存在性检查失败，请稍后重试");
    }

    /**
     * 相对路径安全闸：非空、非绝对/盘符、无 {@code .}/{@code ..}/空段。口径与
     * {@code OpenHandsProvider.safeRelPath}（包内可见、跨包不可调）一致；存在性检查会把路径拼进
     * shell 命令，必须先在本地拦掉穿越。
     */
    private static String safeRelPath(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("沙箱路径非法：路径为空");
        }
        String value = raw.trim().replace('\\', '/');
        if (value.startsWith("/") || value.contains(":")) {
            throw new IllegalArgumentException("沙箱路径非法：必须是相对路径：" + raw);
        }
        StringBuilder kept = new StringBuilder();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("沙箱路径非法：含非法路径段：" + raw);
            }
            if (kept.length() > 0) {
                kept.append('/');
            }
            kept.append(segment);
        }
        return kept.toString();
    }

    /**
     * shell 单引号包裹（内部 {@code '} 用 {@code '\''} 转义），存在性检查路径不逃逸出参数位。
     */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 写入原件字节：先 {@code ensure}（会话缺失按同 id 兜底重建），目标被占用时同目录自动改名，
     * 返回<b>实际落点</b>。路径先过本地安全闸（纯函数）再建会话——非法路径不产生副作用。
     */
    @Override
    public String put(String sessionId, String relPath, byte[] bytes) {
        String safeRel = safeRelPath(relPath);
        ensure(sessionId, List.of());
        String actualRel = nextAvailableRelPath(sessionId, safeRel);
        byte[] content = bytes == null ? new byte[0] : bytes;
        provider.uploadFile(sessionId, actualRel, content);
        log.info("OpenHands 沙箱写入文件: cid={} path={} size={}B",
                sessionId, actualRel, content.length);
        return actualRel;
    }

    /**
     * best-effort 写入：会话不存在（{@code CODE_SESSION_MISSING}）⇒ <b>不创建</b>、返回 false；
     * 其它校验异常原样上抛（不可达/鉴权失败不伪装成"未就绪"，由上传端点兜住）；会话存在时
     * <b>同名直接覆盖为最新字节</b>（上传工作副本语义），不做存在性探测。
     */
    @Override
    public boolean putIfAlive(String sessionId, String relPath, byte[] bytes) {
        try {
            provider.validateSession(sessionId);
        } catch (ServiceException e) {
            if (isPlatformCode(e, AgentRuntimeErrors.CODE_SESSION_MISSING)) {
                return false;
            }
            throw e;
        }
        String safeRel;
        try {
            safeRel = safeRelPath(relPath);
        } catch (IllegalArgumentException e) {
            // 端口契约：路径非法折算 false（best-effort 通道不阻断上传）
            return false;
        }
        byte[] content = bytes == null ? new byte[0] : bytes;
        // 覆盖写：不先 test -e（同名即最新，避免"先查后写"的两拍往返）
        provider.uploadFile(sessionId, safeRel, content);
        log.info("OpenHands 沙箱写入/覆盖文件(best-effort): cid={} path={} size={}B",
                sessionId, safeRel, content.length);
        return true;
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
            if (isPlatformCode(e, AgentRuntimeErrors.CODE_SESSION_MISSING)) {
                // 上游 404（该端点语境＝文件不存在）：固定人话 IAE 给模型自纠（请先 ls），
                // 不透传上游 detail（review #4/#10）
                throw new IllegalArgumentException("文件不存在于沙箱（请先 sandbox_run ls 确认）");
            }
            if (isPlatformCode(e, 40010) && isLocalPathRejection(e)) {
                // 本地 safeRelPath 拒绝（穿越/绝对路径/非法段）＝真路径非法：IAE 带我方固定话术
                // （端口契约：工具层转「拒绝：…」人话给模型自行纠正）
                throw new IllegalArgumentException(stripCodePrefix(e.getMessage()));
            }
            // 其余（上游 400/422 的 40010、不可达、密钥被拒等）＝取件失败：原样上抛，
            // 工具层统一「稍后重试」（review #7：上游 400/422 不冒充路径非法）
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

    /**
     * 判定 40010 是否来自<b>本地</b> {@code OpenHandsProvider.safeRelPath} 拒绝（而非上游 400/422
     * 的 detail，review #7）：safeRelPath 三类拒绝的固定话术都以「工作区路径」开头，路径非法的
     * 判定只认我方话术——上游 detail 一律不进「拒绝：」通道（review #10，不透传上游内容）。
     */
    private static boolean isLocalPathRejection(ServiceException e) {
        String message = e.getMessage();
        return message != null && stripCodePrefix(message).startsWith("工作区路径");
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
