package com.bidr.llm.agent.sandbox;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import com.bidr.llm.agent.session.AgentSessionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

import java.util.regex.Pattern;

/**
 * Title: SessionSandboxPutAttachmentTool
 * Description: 会话受限的沙箱放件工具（原件通道）——把<b>本会话</b>已上传附件的<b>原始字节</b>
 * 放进会话沙箱，供 {@code sandbox_run} 用脚本直接处理（保留 docx/xlsx 等原件的样式、模板与结构，
 * 不做文本解析）。定位口径：{@code AgentFileStore.get(sessionId, fileId)} → {@code read(ref)}
 * → {@code AgentSandbox.put(sessionId, path, bytes)}；<b>只接受会话附件清单中的 file_id</b>，
 * 不暴露 URL/服务器路径参数，模型无法借此把任意本地文件写入沙箱。
 * <p>
 * 落点口径：{@code path} 可选（{@code null}/空白都表示未指定），缺省 {@code input/<原文件名>}；
 * 目标路径已存在时由沙箱端口在同一目录自动改名（{@code <base>-2.<ext>}…）并返回<b>实际落点</b>，
 * 工具文本照实回显。
 * 越权/不存在统一回「附件不存在或不属于本会话」，不携带存储内部细节。
 * 沙箱/存储端口缺席时回明确错误文本，不抛异常（工具语义与 {@code SessionSandboxRunTool} 一致）。
 * <p>
 * 任务作用域：由 {@code AbstractToolLoopAgent#frameworkTools} 按会话新建，持有会话标识。
 *
 * @author Sharp
 * @since 2026/11/16
 */
@Slf4j
public class SessionSandboxPutAttachmentTool {

    /** 工具名（@Tool name 显式声明，e2e/能力包登记按此名核对） */
    public static final String TOOL_NAME = "sandbox_put_attachment";

    /** fileId 形态闸（与 {@code read_attachment} 同口径）：只放行安全字符并限长，
     *  防止模型拼出带路径分隔符/控制字符的伪 fileId 去探存储键 */
    private static final Pattern SAFE_FILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    /** 相对路径非法时的统一人话（不回显非法值；不传/空白＝走默认落点，不算非法） */
    private static final String PATH_REJECTED =
            "拒绝：path 必须是沙箱内相对路径（不得以 / 或盘符开头、不得含 . 或 .. 段、空段、冒号、"
                    + "反斜杠或控制字符）；不传或留空默认 input/<原文件名>";

    /** 越权/不存在统一口径（不 Echo 非法 file_id） */
    private static final String ATTACHMENT_MISSING = "附件不存在或不属于本会话";

    private final String sessionId;

    private final AgentFileStore fileStore;

    private final AgentSandbox sandbox;

    /**
     * 任务作用域构造：按当前会话新建；存储/沙箱 Bean 缺席时保留 null，
     * 调用期回明确错误文本（不在构造期抛，避免工具面装配被一个可选能力击穿）
     */
    public SessionSandboxPutAttachmentTool(AgentSessionContext ctx) {
        this(ctx == null ? null : ctx.getSessionId(),
                beanOrNull(AgentFileStore.class), beanOrNull(AgentSandbox.class));
    }

    /** 可注入构造（测试/自定义装配用）：显式给出会话与依赖 */
    SessionSandboxPutAttachmentTool(String sessionId, AgentFileStore fileStore, AgentSandbox sandbox) {
        this.sessionId = sessionId;
        this.fileStore = fileStore;
        this.sandbox = sandbox;
    }

    /**
     * 把本会话附件原件放进沙箱：按 file_id 取原始字节（不做文本解析），写到指定相对路径或
     * 缺省 {@code input/<原文件名>}；同名自动改名，返回实际落点。
     *
     * @param fileId 会话内附件标识（上传响应/附件清单中的 file_id）
     * @param path   沙箱内目标相对路径（可选；{@code null}/空白＝缺省 {@code input/<原文件名>}；须相对）
     * @return 实际落点与字节数；失败回人话错误文本（不抛）
     */
    @Tool(name = TOOL_NAME, value = "把本会话上传的附件原件（原始字节，不做文本解析）放入沙箱，"
            + "供 sandbox_run 用脚本直接处理（如读取 docx/xlsx 原件，保留原样式与模板）；"
            + "file_id 取自会话附件清单；path 可选，缺省 input/<原文件名>。"
            + "若上传时会话已有沙箱，原件可能已在 input/<文件名>，可先 sandbox_run ls input/ 确认，"
            + "已在则无需重复放入。"
            + "目标路径已存在时会自动改名并在返回中给出实际路径，无需自行换名")
    public String putAttachment(
            @P(name = "file_id", value = "会话附件标识（上传响应或附件清单中的 file_id，不是文件名/URL/路径）") String fileId,
            @P(name = "path", required = false,
                    value = "沙箱内目标相对路径（可选，缺省 input/<原文件名>；不支持绝对路径、..、反斜杠）") String path) {
        String id = fileId == null ? "" : fileId.trim();
        if (!SAFE_FILE_ID.matcher(id).matches()) {
            // 不 Echo 非法值（其中可能含注入串），统一按"不存在"口径
            return ATTACHMENT_MISSING;
        }
        String requestedPath = null;
        // 空白（null/空串/纯空格）＝模型未指定 path：走默认 input/<原文件名>，不按非法路径拒绝（R1-I1）
        if (path != null && !path.trim().isEmpty()) {
            requestedPath = path.trim();
            if (!isValidRelativePath(requestedPath)) {
                return PATH_REJECTED;
            }
        }
        if (sandbox == null) {
            return "沙箱未启用：当前宿主未装配沙箱服务";
        }
        if (fileStore == null) {
            return "附件能力未启用：当前宿主未装配附件存储";
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "沙箱未启用：当前会话标识缺失";
        }
        FileRef ref;
        byte[] bytes;
        try {
            ref = fileStore.get(sessionId, id);
            if (ref == null) {
                return ATTACHMENT_MISSING;
            }
            bytes = fileStore.read(ref);
        } catch (Exception e) {
            log.warn("附件读取失败, sessionId={}, fileId={}, error={}", sessionId, id, e.getMessage());
            return "附件读取失败，请稍后重试";
        }
        if (bytes == null || bytes.length == 0) {
            return "附件为空或读取失败";
        }
        String relPath = requestedPath != null ? requestedPath : defaultPathOf(ref);
        String actualPath;
        try {
            actualPath = sandbox.put(sessionId, relPath, bytes);
        } catch (IllegalArgumentException e) {
            // 路径非法/同名过多（实现侧抛 IAE，message 为我方固定人话）——可自纠错误透传 message
            log.info("沙箱放件入参被拒绝, sessionId={}, path={}, error={}", sessionId, relPath, e.getMessage());
            return "拒绝：" + e.getMessage();
        } catch (Exception e) {
            log.warn("沙箱放件失败, sessionId={}, path={}, error={}", sessionId, relPath, e.getMessage());
            return "放入沙箱失败，请稍后重试";
        }
        if (actualPath == null || actualPath.trim().isEmpty()) {
            // 实现违约（端口契约要求返回实际落点）：按请求路径回显，仍给模型可用信息
            actualPath = relPath;
        }
        log.info("[LLM工具] 会话 '{}' sandbox_put_attachment: fileId={}, size={}B, path={}",
                sessionId, id, bytes.length, actualPath);
        return "已放入沙箱：" + actualPath + "（" + bytes.length
                + " 字节）。可用 sandbox_run 直接处理（工作目录＝沙箱根）。";
    }

    /** 缺省落点：{@code input/<原文件名>}（name 含路径取末段；清洗后仍非法则回落 input/file） */
    private static String defaultPathOf(FileRef ref) {
        String candidate = "input/" + basename(ref == null ? null : ref.name());
        return isValidRelativePath(candidate) ? candidate : "input/file";
    }

    /** 原文件名取 basename（兼容 / 与 \ 分隔）；空名/目录形态/点段回落 file */
    private static String basename(String name) {
        String normalized = name == null ? "" : name.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String base = (slash >= 0 ? normalized.substring(slash + 1) : normalized).trim();
        return base.isEmpty() || ".".equals(base) || "..".equals(base) ? "file" : base;
    }

    /**
     * 相对路径闸：非空、非绝对（/ 开头或盘符开头）、无 {@code .}/{@code ..}/空段、无冒号、
     * 无反斜杠/控制字符。允许中文与空格等普通字符；不做目录创建（沙箱实现侧按需处理）。
     * 与两底座 {@code safeRelPath} 同尺（R1-S3：空段/纯点号段/冒号在工具闸提前拒）。
     */
    private static boolean isValidRelativePath(String path) {
        if (path == null || path.isEmpty() || path.startsWith("/")) {
            return false;
        }
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' || c == ':' || Character.isISOControl(c)) {
                return false;
            }
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return false;
            }
        }
        return true;
    }

    /** 安全取 Bean：缺席/多 Bean（NoUniqueBeanDefinitionException 属 BeansException）一律视为不可用 */
    private static <T> T beanOrNull(Class<T> type) {
        try {
            return BeanUtil.getBean(type);
        } catch (BeansException e) {
            log.debug("{} 解析不唯一/不可用，按能力缺席处理: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}