package com.bidr.llm.agent.sandbox;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.file.AgentArtifact;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.AttachmentContextAssembler;
import com.bidr.llm.agent.file.FileRef;
import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Title: SessionSandboxFetchTool
 * Description: 会话受限的沙箱取件工具（棒3a）——把 {@code sandbox_run} 产在<b>本会话沙箱内</b>的
 * 文件取回给模型/用户：小文本（严格 UTF-8 可解码且 &lt;32KB）直接回正文；大文件/二进制落
 * {@link AgentFileStore}（agent-session 命名空间）签预签名读址，经 {@link AgentEvent#ARTIFACT}
 * 事件给前端发下载卡片（payload 复用 {@link AgentArtifact#toEventPayload()} 六键组装口，
 * 不手拼 Map），并把链接回给模型。只读沙箱内相对路径，不暴露沙箱外文件系统。
 * <p>
 * 沙箱端口缺席回「沙箱未启用」；文件面走产物路线但存储端口缺席时回存储未启用人话
 * （都不抛异常，工具语义与 {@code SessionArtifactGenerateTool} 一致）。事件写失败不回滚
 * 产物：文件已可下载，链接随工具文本照常回给模型，仅记 warn。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class SessionSandboxFetchTool {

    /** 工具名（@Tool name 显式声明，e2e/能力包登记按此名核对） */
    public static final String TOOL_NAME = "sandbox_fetch";

    /** 小文本直回正文的字节上限（严格 UTF-8 可解码前提下的第二道闸） */
    static final int INLINE_TEXT_MAX_BYTES = 32 * 1024;

    private final String sessionId;

    private final AgentSessionContext ctx;

    private final AgentSandbox sandbox;

    private final AgentFileStore fileStore;

    /**
     * 任务作用域构造：按当前会话新建；沙箱/存储端口缺席时保留 null，
     * 调用期回明确错误文本（不在构造期抛，避免工具面装配被一个可选能力击穿）
     */
    public SessionSandboxFetchTool(AgentSessionContext ctx) {
        this(ctx == null ? null : ctx.getSessionId(), ctx,
                beanOrNull(AgentSandbox.class), beanOrNull(AgentFileStore.class));
    }

    /** 可注入构造（测试/自定义装配用）：显式给出会话与依赖 */
    SessionSandboxFetchTool(String sessionId, AgentSessionContext ctx, AgentSandbox sandbox,
                            AgentFileStore fileStore) {
        this.sessionId = sessionId;
        this.ctx = ctx;
        this.sandbox = sandbox;
        this.fileStore = fileStore;
    }

    /**
     * 取回本会话沙箱内的一个文件：小文本直接给全文；大文件/二进制转存为会话产物并回下载链接
     * （同时给前端发下载卡片）。
     *
     * @param path 沙箱内相对路径（如 {@code out/result.csv}；不接受绝对路径/沙箱外路径）
     * @return 小文本＝文件正文；大/二进制＝「文件已取出…下载链接…」；失败回人话错误文本（不抛）
     */
    @Tool(name = TOOL_NAME, value = "取回当前会话沙箱内的一个文件内容（sandbox_run 生成的产出文件）。"
            + "path 传沙箱内相对路径（如 out/result.csv）。小文本文件直接返回全文；"
            + "大文件或二进制文件会转存为可下载产物并返回下载链接（用户侧同时出现下载卡片）")
    public String fetchFile(
            @P(name = "path", value = "沙箱内文件相对路径（不是 URL/绝对路径，如 out/result.csv）") String path) {
        if (path == null || path.trim().isEmpty()) {
            return "拒绝：path 不能为空（沙箱内文件相对路径），补齐后重试";
        }
        if (sandbox == null) {
            return "沙箱未启用：当前宿主未装配沙箱服务";
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "沙箱未启用：当前会话标识缺失";
        }
        String relPath = path.trim();
        byte[] bytes;
        try {
            bytes = sandbox.fetch(sessionId, relPath);
        } catch (IllegalArgumentException e) {
            // 路径非法（穿越/绝对路径等）：文案面向模型可自行纠正（实现侧的拒绝信息即人话）
            log.info("沙箱取件入参被拒绝, sessionId={}, error={}", sessionId, e.getMessage());
            return "拒绝：" + e.getMessage();
        } catch (Exception e) {
            log.warn("沙箱取件失败, sessionId={}, error={}", sessionId, e.getMessage());
            return "沙箱取件失败，请稍后重试";
        }
        if (bytes == null) {
            return "沙箱内文件不存在，请核对相对路径（可用 sandbox_run 执行 ls 查看沙箱目录）后重试";
        }
        if (bytes.length == 0) {
            return "文件为空（0 字节）：" + relPath;
        }
        if (isInlineText(bytes)) {
            log.info("[LLM工具] 会话 '{}' sandbox_fetch: size={}B, inline=true", sessionId, bytes.length);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return viaArtifact(relPath, bytes);
    }

    /** 大文件/二进制路线：转存 AgentFileStore → 预签名读址 → ARTIFACT 事件 → 回链接文本 */
    private String viaArtifact(String relPath, byte[] bytes) {
        if (fileStore == null) {
            return "沙箱取件未启用：当前宿主未装配文件存储（大文件/二进制需要转存为产物）";
        }
        String filename = fileNameOf(relPath);
        FileRef ref;
        String url;
        try {
            ref = fileStore.put(sessionId, filename, "application/octet-stream", bytes);
            url = fileStore.presignedUrl(ref, AttachmentContextAssembler.URL_TTL_SECONDS);
        } catch (Exception e) {
            log.warn("沙箱取件转存失败, sessionId={}, name={}, error={}", sessionId, filename, e.getMessage());
            return "沙箱取件失败（转存暂不可用），请稍后重试";
        }
        if (url == null || url.trim().isEmpty()) {
            log.warn("沙箱取件未取得下载链接, sessionId={}, name={}", sessionId, filename);
            return "沙箱取件失败，请稍后重试";
        }
        AgentArtifact artifact = new AgentArtifact(ref.fileId(), ref.name(), ref.size(), ref.mime(),
                formatOf(ref.name()), url);
        long seq = 0;
        if (ctx != null) {
            try {
                seq = ctx.emit(AgentEvent.ARTIFACT, artifact.toEventPayload());
            } catch (Exception e) {
                // 事件写失败不回滚产物：文件已可下载，链接随工具文本照常回给模型，仅记 warn
                log.warn("产物事件写入失败（下载卡片不会出现，链接仍可用）, sessionId={}, error={}",
                        sessionId, e.getMessage());
            }
        }
        log.info("[LLM工具] 会话 '{}' sandbox_fetch: name={}, size={}B, eventSeq={}",
                sessionId, ref.name(), ref.size(), seq);
        return "文件已取出：" + ref.name() + "（" + ref.size() + "B），下载链接：" + url;
    }

    /** 小文本判据：严格 UTF-8 可解码（REPORT 拒绝非法字节序列）且不超直回上限 */
    private static boolean isInlineText(byte[] bytes) {
        if (bytes.length >= INLINE_TEXT_MAX_BYTES) {
            return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** 展示文件名：取相对路径末段（兼容 / 与 \ 分隔）；末段为空（目录形态）给兜底名 */
    private static String fileNameOf(String relPath) {
        String normalized = relPath.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.trim().isEmpty() ? "sandbox-file" : name;
    }

    /** 产物格式＝扩展名小写（无扩展名给空串，与事件 payload format 键口径一致） */
    private static String formatOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase() : "";
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
