package com.bidr.llm.agent.file;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.document.FileMarkdownTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Title: SessionAttachmentReadTool
 * Description: 会话受限的附件读取工具（C-1 安全收口）——只接受<b>本会话内</b>的
 * {@code file_id}，由框架内部换回完整 {@link FileRef} 并签发读址，再复用
 * {@link FileMarkdownTool} 的解析/分页能力；<b>不暴露 urlOrPath 参数</b>，
 * 模型无法借此读取服务器本地路径或任意 http(s) 地址。
 * <p>
 * 定位口径：{@code AgentFileStore.get(sessionId, fileId)}（事件流重建的引用 key 为 null，
 * 必须经存储侧换回完整引用）→ {@code presignedUrl(ref, 3600)} → {@code read_file} 解析。
 * 越权/不存在统一回「附件不存在或不属于本会话」，不携带存储内部细节（key/异常栈/路径）。
 * 存储端口或文档解析 Bean 缺席时回明确错误文本，不抛异常（工具语义同 {@link FileMarkdownTool}）。
 * <p>
 * 任务作用域：由 {@code AbstractToolLoopAgent#frameworkTools} 按会话新建，持有会话标识；
 * 业务若显式需要 raw {@code read_file}（可读任意地址）自行挂 {@link FileMarkdownTool}，
 * 框架默认不再并入。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
public class SessionAttachmentReadTool {

    /** 工具名（@Tool name 显式声明，e2e/装配单按此名核对） */
    public static final String TOOL_NAME = "read_attachment";

    /** fileId 形态闸：存储实现当前生成 UUID（无连字符），这里只放行安全字符并限长，
     *  防止模型拼出带路径分隔符/控制字符的伪 fileId 去探存储键 */
    private static final Pattern SAFE_FILE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    /** 预签读址缓存有效期（毫秒，m-r2-3）：同一附件分页读取复用同一 URL，
     *  从而命中 {@link FileMarkdownTool} 以 location 为 key 的解析快照（不逐页重下载/重解析）；
     *  5 分钟远小于存储侧签名有效期（1 小时），过期重签即可 */
    private static final long URL_CACHE_TTL_MILLIS = 5 * 60 * 1000L;

    private final String sessionId;

    private final AgentFileStore fileStore;

    private final FileMarkdownTool fileMarkdownTool;

    /** 短 TTL 预签读址缓存：key=(sessionId,fileId)；工具对象按会话新建，仍带 sessionId 维度
     *  以便未来共享实例时不会串会话 */
    private final Map<String, CachedUrl> presignedUrlCache = new ConcurrentHashMap<>();

    /**
     * 任务作用域构造：按当前会话新建；存储/文档 Bean 缺席时保留 null，
     * 调用期回明确错误文本（不在构造期抛，避免工具面装配被一个可选能力击穿）
     */
    public SessionAttachmentReadTool(AgentSessionContext ctx) {
        this(ctx == null ? null : ctx.getSessionId(),
                beanOrNull(AgentFileStore.class), beanOrNull(FileMarkdownTool.class));
    }

    /** 可注入构造（测试/自定义装配用）：显式给出会话与依赖 */
    SessionAttachmentReadTool(String sessionId, AgentFileStore fileStore, FileMarkdownTool fileMarkdownTool) {
        this.sessionId = sessionId;
        this.fileStore = fileStore;
        this.fileMarkdownTool = fileMarkdownTool;
    }

    /**
     * 读取本会话附件正文：只认 file_id（参数面没有 URL/路径），长文件分页续读口径与
     * {@link FileMarkdownTool#readFile(String, int, int)} 一致。
     *
     * @param fileId   会话内附件标识（上传响应/附件清单中的 file_id）
     * @param maxChars 本次最多返回字符数（0/负数按默认 2 万，超过 5 万按 5 万）
     * @param offset   本次读取的起始字符位置（0 起，续读传上次末尾注记的 offset）
     * @return 正文/分页窗口；失败回人话错误文本（不抛）
     */
    @Tool(name = TOOL_NAME, value = "读取本会话上传的附件正文。只接受会话附件清单中的 file_id，"
            + "不接受 URL 或服务器路径；支持文本/office/pdf/图片等常见格式（扫描件与图片会经转录或多模态转文字）。"
            + "长文件分页读：本次返回 offset 起的 maxChars 字，没读到底会在末尾注记下一页的起始 offset，"
            + "续读就传那个数；第一次读传 offset=0")
    public String readAttachment(
            @P(name = "file_id", value = "会话附件标识（上传响应或附件清单中的 file_id，不是文件名/URL/路径）") String fileId,
            @P(name = "max_chars", value = "本次最多返回的字符数，传 0 或负数按默认 2 万，超过 5 万按 5 万") Integer maxChars,
            @P(name = "offset", value = "本次读取的起始字符位置（0 起），续读传上次末尾注记的 offset") Integer offset) {
        String id = fileId == null ? "" : fileId.trim();
        if (id.isEmpty()) {
            return "拒绝：file_id 不能为空，请从会话附件清单中取 file_id 后重试";
        }
        if (!SAFE_FILE_ID.matcher(id).matches()) {
            // 不 Echo 非法值（其中可能含注入串），统一按"不存在"口径
            return "附件不存在或不属于本会话";
        }
        if (fileStore == null) {
            return "附件读取未启用：当前宿主未装配附件存储";
        }
        if (fileMarkdownTool == null) {
            return "附件读取未启用：当前宿主未装配文档解析能力";
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "附件读取未启用：当前会话标识缺失";
        }
        FileRef full;
        try {
            full = fileStore.get(sessionId, id);
        } catch (Exception e) {
            log.warn("附件定位失败（按不存在处理）, sessionId={}, fileId={}, error={}",
                    sessionId, id, e.getMessage());
            return "附件不存在或不属于本会话";
        }
        if (full == null) {
            return "附件不存在或不属于本会话";
        }
        String url;
        try {
            url = cachedPresignedUrl(full, id);
        } catch (Exception e) {
            log.warn("附件读址签发失败, sessionId={}, fileId={}, error={}", sessionId, id, e.getMessage());
            return "附件读取失败，请稍后重试";
        }
        if (url == null || url.trim().isEmpty()) {
            log.warn("附件读址为空, sessionId={}, fileId={}", sessionId, id);
            return "附件读取失败，请稍后重试";
        }
        try {
            return fileMarkdownTool.readFile(url, maxChars == null ? 0 : maxChars,
                    offset == null ? 0 : offset);
        } catch (Exception e) {
            // 解析器已把常规失败转文本；这里兜住意外异常，仍不向模型暴露内部细节
            log.warn("附件解析异常, sessionId={}, fileId={}, error={}", sessionId, id, e.getMessage());
            return "附件读取失败，请稍后重试";
        }
    }

    /**
     * 取预签读址（m-r2-3）：同一 {@code (sessionId,fileId)} 在短 TTL 内复用同一 URL。
     * 分页续读因此命中 {@link FileMarkdownTool} 的快照缓存（其 key 是完整 location），
     * 避免每次调用都重新下载并整文件解析。
     */
    private String cachedPresignedUrl(FileRef full, String fileId) {
        String cacheKey = sessionId + "\u0000" + fileId;
        long now = System.currentTimeMillis();
        CachedUrl cached = presignedUrlCache.get(cacheKey);
        if (cached != null && cached.expiresAt > now) {
            return cached.url;
        }
        String url = fileStore.presignedUrl(full, AttachmentContextAssembler.URL_TTL_SECONDS);
        if (url != null && !url.trim().isEmpty()) {
            presignedUrlCache.put(cacheKey, new CachedUrl(url, now + URL_CACHE_TTL_MILLIS));
        }
        return url;
    }

    /** 预签读址缓存项（url + 过期时刻） */
    private static final class CachedUrl {

        private final String url;

        private final long expiresAt;

        private CachedUrl(String url, long expiresAt) {
            this.url = url;
            this.expiresAt = expiresAt;
        }
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