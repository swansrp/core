package com.bidr.cube;

import com.bidr.cube.admin.AdminSandboxManager;
import com.bidr.cube.client.CubeHttpClient;
import com.bidr.cube.config.CubeSandboxProperties;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: CubeSandboxProvider
 * Description: {@link AgentSandbox} 的 Cube 底座实现（会话级容器沙箱：一个会话一个沙箱实例，
 * 业务沙箱把宿主 {@code {hostRoot}/{sessionId}} 挂到容器 {@code /workspace}，种子/取件走文件面，
 * exec 走数据面 Connect 信封）。生命周期由内存 map（sessionId→sandboxId）＋metadata 对账
 * 共同兜底（D3 拍板：重启丢 map 后按 {@code session-id} metadata 对账既有实例，不盲目重建）：
 * <ul>
 *   <li>{@link #ensure}：<b>顺序红线</b>——先 {@code adminSandbox.ensureDir}（宿主目录不存在
 *       ⇒ create 130545 失败，实测坑）→ 内存 map 命中即复用；miss ⇒ 按 metadata 对账
 *       （列表异常降级直接 create＋warn，行为不断）；最后种子逐个 writeFile（同路径覆盖
 *       重传＝幂等，照端口契约）；</li>
 *   <li>{@link #exec}：合并 stdout/stderr（stdout 在前）后按 64KB UTF-8 字节截断；超时
 *       置 timedOut。<b>注意</b>：底座 exec 超时只断开本次 HTTP，远程进程不显式 kill——
 *       兜底＝沙箱 idle 回收（到期整沙箱销毁，进程随容器消亡），不在 Provider 层重复 kill；</li>
 *   <li>{@link #fetch}：相对路径校验（空／绝对路径／盘符／{@code .}/{@code ..}／空段 ⇒ IAE，
 *       工具层统一加「拒绝：」前缀转人话）；文件不存在转 IAE 固定话术
 *       「文件不存在于沙箱（请先 sandbox_run ls 确认）」给模型自纠，不回 null；</li>
 *   <li>{@link #destroy}：{@code client.delete}（404 静默＝幂等）＋map 逐出；map miss 时按
 *       metadata 对账寻址（重启后也能收口）；<b>宿主目录 {@code {hostRoot}/{sessionId}} 不删</b>
 *       （归档策略后续批次定）。</li>
 * </ul>
 * <p>
 * {@link #mergeOutput}/{@link #truncateUtf8} 为 {@code OpenHandsSandboxProvider} 同名方法的
 * <b>刻意复制</b>（逻辑逐字等价，审查按两份实现逐字等价核对）：复制原因＝模块解耦——
 * sandbox-cube 🔴 禁依赖 agent-capability/agent-runtime（装配互斥腿，pom 依赖方向只有 llm），
 * 故无法复用彼侧纯函数，只能本模块复制；任一侧修改须同步另一侧。
 *
 * @author Sharp
 * @since 2026/11/16
 */
@Slf4j
public class CubeSandboxProvider implements AgentSandbox {

    /**
     * 业务沙箱容器内工作区挂载点（create 的 host-mount：{hostRoot}/{sessionId} → /workspace），
     * 种子写入与 fetch 寻址都落在它下面。
     */
    private static final String WORKSPACE_MOUNT = "/workspace";

    /**
     * exec 输出合并后的截断上限（UTF-8 字节）：防超长输出（如 cat 大文件）撑爆模型上下文；
     * 截断时在尾部追加标记让模型知道输出不完整。（刻意复制自 OpenHandsSandboxProvider，见类注释）
     */
    static final int OUTPUT_MAX_BYTES = 64 * 1024;

    /** 截断标记（不计入上限，追加在尾部）。（刻意复制自 OpenHandsSandboxProvider，见类注释） */
    static final String TRUNCATION_NOTE = "\n…[沙箱输出超长，已截断]";

    /** fetch 文件不存在的固定人话（照 OpenHandsSandboxProvider 语义；工具层加「拒绝：」前缀转模型话术） */
    private static final String FILE_NOT_FOUND_PHRASE = "文件不存在于沙箱（请先 sandbox_run ls 确认）";

    private final CubeHttpClient client;

    private final AdminSandboxManager admin;

    private final CubeSandboxProperties properties;

    /** 会话级沙箱实例表（sessionId → sandboxId）；Provider 重启后靠 metadata 对账重建 */
    private final Map<String, String> sandboxIds = new ConcurrentHashMap<>();

    public CubeSandboxProvider(CubeHttpClient client, AdminSandboxManager admin,
                               CubeSandboxProperties properties) {
        this.client = client;
        this.admin = admin;
        this.properties = properties;
    }

    /**
     * 懒建沙箱会话并预置种子文件（幂等；种子按清单全量重传，同路径覆盖＝最新为准）。
     * 顺序红线：宿主目录预创建必须先于 create。
     * <p>
     * 方法级 {@code synchronized}：会话内 agent loop 对本方法的调用是串行的，正常路径零
     * 争用；加锁防未来并发展开——同一会话多线程同时 ensure 时「查 map→对账/建箱→回填 map」
     * 非原子，会重复建箱（M-3）。
     */
    @Override
    public synchronized void ensure(String sessionId, List<SandboxSeedFile> seeds) {
        // ① 顺序红线：先经管理沙箱 mkdir -p 宿主会话目录——hostPath 不存在 ⇒ create 130545 失败（实测坑）
        admin.ensureDir(sessionId);
        // ② 会话沙箱：内存命中复用；miss ⇒ metadata 对账（列表异常降级直接 create＋warn）
        String sandboxId = sandboxIds.get(sessionId);
        if (sandboxId == null) {
            sandboxId = reconcileOrCreate(sessionId);
            sandboxIds.put(sessionId, sandboxId);
        }
        // ③ 种子全量重传（覆盖写＝幂等）
        if (seeds == null || seeds.isEmpty()) {
            return;
        }
        for (SandboxSeedFile seed : seeds) {
            client.writeFile(sandboxId, workspacePath(seed.relPath()), seed.content());
        }
        log.info("Cube 沙箱种子预置完成: session={} sandbox={} files={}",
                sessionId, sandboxId, seeds.size());
    }

    @Override
    public SandboxExecResult exec(String sessionId, String command, long timeoutMs) {
        // 入参 ≤0 兜底取 exec 缺省超时配置（秒→毫秒），再 clamp ≥1000ms（M-5）：配置误填
        // 0/负值也不产生非正 Duration（HTTP client 会抛 IllegalArgumentException: Invalid duration）
        long effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs
                : Math.max(1000L, properties.getTimeout() * 1000L);
        CubeHttpClient.ExecResult result = client.exec(resolveSandboxId(sessionId), command, effectiveTimeoutMs);
        // 🔴 mergeOutput 内部已做 64KB 截断（与 OH 逐字等价）——此处不得再套一层 truncateUtf8
        // （双重截断会把截断标记再劈一刀，产出带游离 \n 的脏 head——实测坑）
        return new SandboxExecResult(result.exitCode(),
                mergeOutput(result.stdout(), result.stderr()),
                result.timedOut());
    }

    @Override
    public byte[] fetch(String sessionId, String relPath) {
        String path = workspacePath(relPath);
        try {
            return client.readFile(resolveSandboxId(sessionId), path);
        } catch (ServiceException e) {
            if (isNotFound(e)) {
                // 上游 404（文件不存在）：固定人话 IAE 给模型自纠（请先 ls），不透传上游响应体
                throw new IllegalArgumentException(FILE_NOT_FOUND_PHRASE);
            }
            // 其余（网络异常/5xx 等）＝取件失败：原样上抛，工具层统一「稍后重试」
            throw e;
        }
    }

    @Override
    public void destroy(String sessionId) {
        String sandboxId = sandboxIds.remove(sessionId);
        if (sandboxId == null) {
            // map miss（重启丢内存/重复销毁）：按 metadata 对账寻址；列表为空＝本就不存在，幂等静默
            List<String> ids;
            try {
                ids = client.listSandboxIds("session-id", sessionId);
            } catch (RuntimeException e) {
                throw new ServiceException("destroy 沙箱对账失败（会话 " + sessionId + "）: " + e.getMessage());
            }
            if (ids.isEmpty()) {
                return;
            }
            sandboxId = ids.get(0);
        }
        client.delete(sandboxId);
        // 🔴 宿主目录 {hostRoot}/{sessionId} 不删：产物归档策略后续批次定，届时统一收口
    }

    // ---------------------------------------------------------------- 内部

    /**
     * D3 对账（ensure 通道）：内存 miss ⇒ 按 {@code session-id} metadata 对账既有实例
     * （M-10：GET /sandboxes 返回含 metadata 的运行中列表）；列表接口异常 ⇒ 降级直接
     * create＋warn（create 自带 ×4 瞬态重试），对账缺失最多造成一次多余实例，行为不断。
     */
    private String reconcileOrCreate(String sessionId) {
        try {
            List<String> ids = client.listSandboxIds("session-id", sessionId);
            if (!ids.isEmpty()) {
                log.info("Cube 沙箱对账命中既有实例: session={} sandbox={}", sessionId, ids.get(0));
                return ids.get(0);
            }
        } catch (RuntimeException e) {
            log.warn("Cube 沙箱对账失败，降级直接新建: session={} err={}", sessionId, e.getMessage());
        }
        return client.create(sessionId).sandboxId();
    }

    /**
     * exec/fetch 寻址：内存命中直用；miss ⇒ metadata 对账（命中记回 map）；对账异常/空列表
     * ⇒ 明确报错（exec/fetch 前置是 ensure，会话缺失属调用方状态错误，不静默新建）。
     */
    private String resolveSandboxId(String sessionId) {
        String sandboxId = sandboxIds.get(sessionId);
        if (sandboxId != null) {
            return sandboxId;
        }
        List<String> ids;
        try {
            ids = client.listSandboxIds("session-id", sessionId);
        } catch (RuntimeException e) {
            throw new ServiceException("Cube 沙箱对账失败（会话 " + sessionId + "）: " + e.getMessage());
        }
        if (ids.isEmpty()) {
            throw new ServiceException("Cube 沙箱会话不存在（须先 ensure 建会话）: " + sessionId);
        }
        sandboxIds.put(sessionId, ids.get(0));
        log.info("Cube 沙箱对账命中既有实例: session={} sandbox={}", sessionId, ids.get(0));
        return ids.get(0);
    }

    /** 沙箱内绝对路径＝/workspace/&lt;safeRelPath&gt;（挂载点见 WORKSPACE_MOUNT；relPath 先过安全闸） */
    private static String workspacePath(String relPath) {
        return WORKSPACE_MOUNT + "/" + safeRelPath(relPath);
    }

    /**
     * 相对路径安全闸（读写两侧共用同一把尺子；行为对齐 agent-runtime 的 OpenHandsProvider.
     * safeRelPath——彼侧禁依赖只能复制口径，非逐字复制）：硬拒四类——空、绝对路径／盘符、
     * {@code .}/{@code ..}/空路径段。不做字符白名单（技能包成员名与沙箱产物名由内容决定，
     * 中文与空格都要能落）。IAE message 为人话（工具层统一加「拒绝：」前缀，故此处不带）。
     */
    static String safeRelPath(String raw) {
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
     * 判定 ServiceException 是否为「文件不存在」类：只认 {@link CubeHttpClient#readFile}
     * 失败消息的固定前缀 {@code readFile 失败 HTTP 404:}（code 后带冒号防 4041 等前缀串咬）。
     * relPath 只出现在该消息的 detail 段——路径含 "HTTP 404" 字样也不会误判（M-1）。
     */
    private static boolean isNotFound(ServiceException e) {
        String message = e.getMessage();
        return message != null && message.startsWith("readFile 失败 HTTP 404:");
    }

    /**
     * stdout/stderr 合并（stdout 在前、stderr 换行接后）。
     * （刻意复制自 OpenHandsSandboxProvider.mergeOutput，逻辑逐字等价——见类注释解耦原因）
     */
    static String mergeOutput(String stdout, String stderr) {
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
     * （刻意复制自 OpenHandsSandboxProvider.truncateUtf8，逻辑逐字等价——见类注释解耦原因）
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
}
