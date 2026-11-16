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
 *   <li>{@link #put}：先 {@code ensure}，目标路径被占用时<b>同目录自动改名</b>
 *       （{@code <base>-2.<ext>}…，上限 100 次），写入原件字节并返回<b>实际落点</b>；</li>
 *   <li>{@link #putIfAlive}：仅当内存表命中（沙箱已就绪）时写入——<b>同名覆盖为最新字节</b>
 *       （上传工作副本语义）；<b>绝不 ensure/create</b>（重启丢 map 视为未就绪，后续工具调用会再对账）；</li>
 *   <li>{@link #destroy}：{@code client.delete}（404 静默＝幂等）＋map 逐出；map miss 时按
 *       metadata 对账寻址（重启后也能收口）；<b>宿主目录 {@code {hostRoot}/{sessionId}} 不删</b>
 *       （归档策略后续批次定）。</li>
 * </ul>
 * <p>
 * 存在性检查统一走 {@code exec test -e <绝对路径>}（{@code /workspace/<rel>}，不依赖 cwd）：
 * exit 0＝存在、1＝不存在、超时/其它码＝检查失败（ServiceException 交工具层「稍后重试」）。
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

    /** 同名改名探测上限（{@code -2}…{@code -101} 共 100 次；超限抛固定人话 IAE） */
    private static final int MAX_RENAMES = 100;

    /** 存在性检查 exec 超时（毫秒）：{@code test -e} 正常毫秒级，纯上限 */
    private static final long EXISTENCE_CHECK_TIMEOUT_MS = 10_000L;

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

    /**
     * 写入原件字节：先 {@code ensure}，目标被占用时同目录自动改名，返回<b>实际落点</b>。
     * 路径先过安全闸（纯函数）再建箱——非法路径不产生沙箱副作用。
     */
    @Override
    public String put(String sessionId, String relPath, byte[] bytes) {
        String safeRel = safeRelPath(relPath);
        ensure(sessionId, List.of());
        String sandboxId = resolveSandboxId(sessionId);
        String actualRel = nextAvailableRelPath(sessionId, safeRel);
        byte[] content = bytes == null ? new byte[0] : bytes;
        client.writeFile(sandboxId, workspacePath(actualRel), content);
        log.info("Cube 沙箱写入文件: session={} sandbox={} path={} size={}B",
                sessionId, sandboxId, actualRel, content.length);
        return actualRel;
    }

    /**
     * best-effort 写入：<b>绝不 ensure/create</b>——内存表 miss（含重启丢 map）一律返回 false，
     * 后续工具调用 {@code put} 会 ensure 对账再写；沙箱存在时<b>同名直接覆盖为最新字节</b>
     * （上传工作副本语义：{@code temp.txt} 这类重复名永远是最新那版），不做存在性探测。
     */
    @Override
    public boolean putIfAlive(String sessionId, String relPath, byte[] bytes) {
        String sandboxId = sandboxIds.get(sessionId);
        if (sandboxId == null) {
            return false;
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
        client.writeFile(sandboxId, workspacePath(safeRel), content);
        log.info("Cube 沙箱写入/覆盖文件(best-effort): session={} sandbox={} path={} size={}B",
                sessionId, sandboxId, safeRel, content.length);
        return true;
    }

    @Override
    public SandboxExecResult exec(String sessionId, String command, long timeoutMs) {
        // 入参 ≤0 兜底取 exec 缺省超时配置（秒→毫秒），再 clamp ≥1000ms（M-5）：配置误填
        // 0/负值也不产生非正 Duration（HTTP client 会抛 IllegalArgumentException: Invalid duration）
        long effectiveTimeoutMs = timeoutMs > 0 ? timeoutMs
                : Math.max(1000L, properties.getTimeout() * 1000L);
        // 🔴 会话工作区语义（2026-11-16 真机发现）：种子/put/exists 全部按绝对路径落
        // {@code /workspace}，但 Cube 容器默认 cwd＝{@code /root}——不前置 cd 时模型按
        // 「工作目录＝沙箱根」用相对路径会找不到文件（引擎B 真机实测 pwd=/root）。
        // 统一包一层 {@code cd '/workspace' && <cmd>}，让相对路径与工具文案口径一致；
        // 需要用绝对路径/自行 cd 的命令不受影响。
        String wrapped = "cd " + shellQuote(WORKSPACE_MOUNT) + " && " + command;
        CubeHttpClient.ExecResult result = client.exec(resolveSandboxId(sessionId), wrapped, effectiveTimeoutMs);
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
     * 沙箱内存在性检查（统一 {@code exec test -e}，传绝对路径不依赖 cwd）：exit 0＝存在、
     * 1＝不存在、超时/其它退出码＝检查失败（ServiceException 交工具层「稍后重试」，不误判）。
     */
    private boolean exists(String sessionId, String relPath) {
        // 绝对路径不依赖 cwd：直接走 client.exec（不经公开 exec 的 `cd /workspace` 包装，
        // 命令串保持逐字可断言；包装与否对 test -e 结果无影响）
        CubeHttpClient.ExecResult result = client.exec(resolveSandboxId(sessionId),
                "test -e " + shellQuote(workspacePath(relPath)), EXISTENCE_CHECK_TIMEOUT_MS);
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
     * shell 单引号包裹（内部 {@code '} 用 {@code '\''} 转义），存在性检查路径不逃逸出参数位。
     * 与 {@code AdminSandboxManager.shellQuote} 同口径；本类自写一份，不做跨模块可见性放宽。
     */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
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
