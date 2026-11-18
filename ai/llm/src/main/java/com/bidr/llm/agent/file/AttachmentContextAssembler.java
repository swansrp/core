package com.bidr.llm.agent.file;

import com.bidr.kernel.utils.BeanUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Title: AttachmentContextAssembler
 * Description: 会话附件 → 模型可见上下文的公共拼装器（首轮前置注入与运行期逐轮增量注入共用一份实现）：
 * <ul>
 *     <li><b>白名单文本小件</b>（{@code text/plain|text/markdown|text/csv|application/json} 且不超
 *     {@code llm.agent.files.inline-text-max-bytes}）：UTF-8 解码后作为围栏块直接给出原文；</li>
 *     <li><b>其余</b>（office/pdf/图片/大文本/未知 MIME）：只给 {@code file_id} 指令，由会话受限工具
 *     {@code read_attachment} 按需读取（不再把任意 URL 当工具入参，C-1）。</li>
 * </ul>
 * 安全约束（I-5）：
 * <ul>
 *     <li>单批内联总量受 {@code llm.agent.files.inline-total-max-bytes}（缺省 128KB）约束，
 *     超额按剩余额度截断并标注「…（已截断）」；</li>
 *     <li>文件名剥离换行/反引号/控制字符并限长 64；正文剥离控制字符，并把 {@code ```} 围栏替换为
 *     不可闭合形态（{@code ``\u200B`}），防止提前闭合并注入指令；</li>
 *     <li>MIME 白名单只认显式声明的基础类型（带 {@code ;charset=} 后缀可识别），
 *     二进制被标成 text/plain 时不会直注乱码。</li>
 * </ul>
 * 单附件定位/读取/签址异常只记 warn 并跳过，不阻断会话；无附件或存储端口缺席返回空串，
 * 调用方不拼（提示词与改造前逐字节一致）。
 * <p>
 * 事件流重建的引用 {@code key} 为 null（事件协议不携存储键），故按 fileId 经
 * {@link AgentFileStore#get(String, String)} 换回完整引用再读/签址；预签 URL 只用于
 * 存储可用性校验与掩码日志，<b>不进提示词</b>。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
public final class AttachmentContextAssembler {

    /** 附件读址预签名有效期（秒）：与 BlobStore 的固定签名口径（1 小时）对齐 */
    public static final long URL_TTL_SECONDS = 3600L;

    /** 小文本附件直接注入正文的字节上限缺省值（与 {@code llm.agent.files.inline-text-max-bytes} 同键） */
    public static final long DEFAULT_INLINE_TEXT_MAX_BYTES = 65536L;

    /** 单批内联正文的总字节上限缺省值（与 {@code llm.agent.files.inline-total-max-bytes} 同键） */
    public static final long DEFAULT_INLINE_TOTAL_MAX_BYTES = 131072L;

    /** 允许直注正文的 MIME 白名单（基础类型，小写；带 charset 参数时先剥离） */
    private static final Set<String> INLINE_MIME_TYPES = new HashSet<>(Arrays.asList(
            "text/plain", "text/markdown", "text/csv", "application/json"));

    /** 小文本注入上限配置键（与框架 {@code @Value} 同键，运行期逐轮注入侧从这里取） */
    private static final String INLINE_TEXT_MAX_BYTES_KEY = "llm.agent.files.inline-text-max-bytes";

    /** 单批内联总量配置键（同上） */
    private static final String INLINE_TOTAL_MAX_BYTES_KEY = "llm.agent.files.inline-total-max-bytes";

    /** 附件工具总开关配置键（整体回退口径，见 {@link #resolveFileToolsEnabled()}） */
    private static final String FILE_TOOLS_ENABLED_KEY = "llm.agent.file-tools.enabled";

    /** 展示用文件名最大长度（防长名/伪扩展名灌提示词） */
    private static final int MAX_DISPLAY_NAME_LENGTH = 64;

    /** 截断标记（正文超单批总量时追加） */
    private static final String TRUNCATED_MARK = "\n…（已截断）";

    private AttachmentContextAssembler() {
    }

    /**
     * 拼装一批附件的说明文本（按入参顺序、空行分隔）。无附件/存储端口缺席/全部附件定位失败返回空串。
     *
     * @param fileStore            附件存储端口（非空；调用方已判定在场）
     * @param sessionId            会话标识（存储侧定位维度）
     * @param attachments          附件引用清单（可含 key=null 的事件重建引用）
     * @param inlineTextMaxBytes   单件小文本直接注入正文的字节上限
     * @param inlineTotalMaxBytes  本批内联正文的总字节上限（超则截断标注）
     */
    public static String assemble(AgentFileStore fileStore, String sessionId,
                                  Collection<FileRef> attachments, long inlineTextMaxBytes,
                                  long inlineTotalMaxBytes) {
        if (fileStore == null || attachments == null || attachments.isEmpty()) {
            return "";
        }
        long remainingInlineBytes = Math.max(inlineTotalMaxBytes, 0L);
        StringBuilder text = new StringBuilder();
        int injected = 0;
        for (FileRef attachment : new ArrayList<>(attachments)) {
            if (attachment == null || attachment.fileId() == null) {
                continue;
            }
            Block block = blockOf(fileStore, sessionId, attachment, inlineTextMaxBytes, remainingInlineBytes);
            if (block == null || block.text.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append("\n\n");
            }
            text.append(block.text);
            remainingInlineBytes = Math.max(remainingInlineBytes - block.inlineBytes, 0L);
            injected++;
        }
        if (injected > 0) {
            log.info("会话附件上下文已生成: sessionId={}, 附件数={}/{}, 字符数={}, 剩余内联额度={}",
                    sessionId, injected, attachments.size(), text.length(), remainingInlineBytes);
        }
        return text.toString();
    }

    /** 单附件说明块：白名单文本给原文，其余给 file_id 指令；定位/读取/签址失败返回 null（跳过该附件） */
    private static Block blockOf(AgentFileStore fileStore, String sessionId, FileRef attachment,
                                 long inlineTextMaxBytes, long remainingInlineBytes) {
        FileRef full;
        try {
            full = fileStore.get(sessionId, attachment.fileId());
        } catch (Exception e) {
            log.warn("会话附件定位失败（跳过该附件）, sessionId={}, fileId={}, error={}",
                    sessionId, attachment.fileId(), e.getMessage());
            return null;
        }
        if (full == null) {
            log.warn("会话附件在存储中不存在（跳过该附件）, sessionId={}, fileId={}",
                    sessionId, attachment.fileId());
            return null;
        }
        String label = "【附件 " + safeDisplayName(full.name()) + "（" + full.size() + " 字节）";
        if (isInlineText(full, inlineTextMaxBytes) && remainingInlineBytes > 0) {
            try {
                byte[] bytes = fileStore.read(full);
                if (bytes != null && bytes.length > 0) {
                    long allowed = Math.min(Math.min(bytes.length, inlineTextMaxBytes), remainingInlineBytes);
                    if (allowed > 0) {
                        SanitizedBody body = decodeAndSanitize(bytes, allowed);
                        if (!body.text.isEmpty()) {
                            long consumed = utf8Length(body.text);
                            log.info("会话附件按原文注入: sessionId={}, fileId={}, name={}, size={}, 注入字节={}, 截断={}",
                                    sessionId, full.fileId(), safeDisplayName(full.name()), full.size(),
                                    consumed, body.truncated);
                            return new Block(label + "原文】\n```\n" + body.text
                                    + (body.truncated ? TRUNCATED_MARK : "") + "\n```", consumed);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("会话附件原文读取失败（回落 file_id 指令）, sessionId={}, fileId={}, error={}",
                        sessionId, full.fileId(), e.getMessage());
            }
        }
        try {
            String url = fileStore.presignedUrl(full, URL_TTL_SECONDS);
            if (url == null || url.trim().isEmpty()) {
                log.warn("会话附件读址为空（跳过该附件）, sessionId={}, fileId={}", sessionId, full.fileId());
                return null;
            }
            // 读址只用于存储可用性校验与掩码日志：不把可被模型复用的任意 URL 放进提示词（C-1）
            log.info("会话附件按 file_id 注入: sessionId={}, fileId={}, name={}, url={}",
                    sessionId, full.fileId(), safeDisplayName(full.name()), maskedUrl(url));
            return new Block(label + "，file_id=" + safeFileId(full.fileId())
                    + "：请用 read_attachment 读取】", 0L);
        } catch (Exception e) {
            log.warn("会话附件读址签发失败（跳过该附件）, sessionId={}, fileId={}, error={}",
                    sessionId, full.fileId(), e.getMessage());
            return null;
        }
    }

    /**
     * 小文本判定：MIME 命中白名单（基础类型）且元数据字节数不超配置上限。
     * 未知/非白名单 MIME 不内联（走 file_id 指令由工具解析）。
     */
    public static boolean isInlineText(FileRef ref, long inlineTextMaxBytes) {
        if (ref == null || ref.size() > inlineTextMaxBytes) {
            return false;
        }
        return INLINE_MIME_TYPES.contains(baseMime(ref.mime()));
    }

    /** 基础 MIME：小写、去参数（{@code text/plain; charset=utf-8} → {@code text/plain}） */
    static String baseMime(String mime) {
        if (mime == null) {
            return "";
        }
        String base = mime;
        int semi = base.indexOf(';');
        if (semi >= 0) {
            base = base.substring(0, semi);
        }
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /** 文件名清洗：剥离换行/反引号/控制字符，折叠首尾空白，限长 64；空名回落 file */
    static String safeDisplayName(String name) {
        if (name == null) {
            return "file";
        }
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '\n' || c == '\r' || c == '`' || c < 0x20 || c == 0x7F) {
                continue;
            }
            // 方括号是附件说明块的定界符，替换为全角括号等义字符，避免提前闭合标签
            if (c == '【' || c == '】') {
                out.append(c == '【' ? '(' : ')');
            } else {
                out.append(c);
            }
        }
        String cleaned = out.toString().trim();
        if (cleaned.isEmpty()) {
            return "file";
        }
        return cleaned.length() > MAX_DISPLAY_NAME_LENGTH
                ? cleaned.substring(0, MAX_DISPLAY_NAME_LENGTH) : cleaned;
    }

    /** 正文清洗：保留换行/回车/制表，剥离其余控制字符；``` 围栏替换为不可闭合形态 */
    static String sanitizeBody(String body) {
        StringBuilder out = new StringBuilder(body.length());
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') {
                out.append(c);
            } else if (c >= 0x20 && c != 0x7F) {
                out.append(c);
            }
        }
        return out.toString().replace("```", "``\u200B`");
    }

    /**
     * 解码、清洗并按 UTF-8 字节额度截断（不切断多字节字符）。
     * <p>截断判定按<b>清洗后的完整正文</b>与 maxBytes 比较（m-r2-5）：围栏替换
     * （{@code ```} → {@code ``\u200B`}）会增大字节数，若先按原文判"未超限"再在清洗后二次截断，
     * 模型会拿到没有「…（已截断）」标记的残文。这里只做一次口径：完整清洗 → 比额度 → 截断。
     */
    private static SanitizedBody decodeAndSanitize(byte[] bytes, long maxBytes) {
        String sanitizedFull = sanitizeBody(new String(bytes, StandardCharsets.UTF_8));
        boolean truncated = utf8Length(sanitizedFull) > maxBytes;
        String text = truncated ? truncateUtf8(sanitizedFull, maxBytes) : sanitizedFull;
        return new SanitizedBody(text, truncated);
    }

    /** 按 UTF-8 字节数截断字符串（保留完整码点） */
    static String truncateUtf8(String value, long maxBytes) {
        if (value == null || value.isEmpty() || maxBytes <= 0) {
            return "";
        }
        long total = 0L;
        int end = 0;
        while (end < value.length()) {
            int codePoint = value.codePointAt(end);
            int bytes = utf8Length(codePoint);
            if (total + bytes > maxBytes) {
                break;
            }
            total += bytes;
            end += Character.charCount(codePoint);
        }
        return value.substring(0, end);
    }

    /** UTF-8 编码字节数 */
    static long utf8Length(String value) {
        if (value == null) {
            return 0L;
        }
        long total = 0L;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            total += utf8Length(codePoint);
            i += Character.charCount(codePoint);
        }
        return total;
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return 3;
        }
        return 4;
    }

    /** 提示词里只放安全 fileId 形态（存储实现异常时也不把控制字符带进提示词） */
    private static String safeFileId(String fileId) {
        if (fileId == null) {
            return "";
        }
        String cleaned = fileId.replaceAll("[^A-Za-z0-9_-]", "");
        return cleaned.length() > 128 ? cleaned.substring(0, 128) : cleaned;
    }

    /**
     * 日志用 URL 掩码（m-1）：只保留 host(:port) 与路径尾 8 位，
     * 不把完整预签名串写进日志（日志可见≠拥有读权）
     */
    static String maskedUrl(String url) {
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost() == null ? "unknown-host" : uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            String tail = path.length() <= 8 ? path : path.substring(path.length() - 8);
            return host + (uri.getPort() > 0 ? ":" + uri.getPort() : "") + " pathTail=" + tail;
        } catch (Exception e) {
            return "unparsable-url";
        }
    }

    /**
     * 运行期解析小文本注入上限（{@code llm.agent.files.inline-text-max-bytes}，缺省 64KB）：
     * 供非 Spring 托管对象（{@link com.bidr.llm.agent.session.AgentSessionContext}）逐轮注入侧取配置；
     * 容器未就绪/取值异常一律回落缺省，不影响会话。
     */
    public static long resolveInlineTextMaxBytes() {
        return resolveLong(INLINE_TEXT_MAX_BYTES_KEY, DEFAULT_INLINE_TEXT_MAX_BYTES);
    }

    /**
     * 运行期解析单批内联总量上限（{@code llm.agent.files.inline-total-max-bytes}，缺省 128KB）；
     * 取数口径同 {@link #resolveInlineTextMaxBytes()}。
     */
    public static long resolveInlineTotalMaxBytes() {
        return resolveLong(INLINE_TOTAL_MAX_BYTES_KEY, DEFAULT_INLINE_TOTAL_MAX_BYTES);
    }

    /**
     * 运行期解析附件工具总开关（{@code llm.agent.file-tools.enabled}，缺省 true）。
     * <p><b>整体回退口径</b>（I-r2-1）：关＝工具面、首轮/逐轮附件上下文注入、能力位三处一起退，
     * 提示词与改造前逐字节一致；非 Spring 托管对象（{@code AgentSessionContext}）经本口读同一键。
     * 容器未就绪/取值异常按缺省 true（可用路径），与 inline 上限取数口径一致。
     */
    public static boolean resolveFileToolsEnabled() {
        try {
            String raw = BeanUtil.getProperty(FILE_TOOLS_ENABLED_KEY);
            return raw == null || raw.trim().isEmpty() || Boolean.parseBoolean(raw.trim());
        } catch (Exception e) {
            log.debug("附件总开关读取失败（按缺省 true）, key={}, error={}", FILE_TOOLS_ENABLED_KEY, e.getMessage());
            return true;
        }
    }

    private static long resolveLong(String key, long defaultValue) {
        try {
            String raw = BeanUtil.getProperty(key);
            if (raw != null && !raw.trim().isEmpty()) {
                return Long.parseLong(raw.trim());
            }
        } catch (Exception e) {
            log.debug("附件内联上限读取失败（按缺省 {} 字节）, key={}, error={}", defaultValue, key, e.getMessage());
        }
        return defaultValue;
    }

    /** 清洗后的正文与截断标记（按清洗后字节数判定，见 {@link #decodeAndSanitize}） */
    private static final class SanitizedBody {

        private final String text;

        private final boolean truncated;

        private SanitizedBody(String text, boolean truncated) {
            this.text = text;
            this.truncated = truncated;
        }
    }

    /** 单附件说明块：text 为拼装文本，inlineBytes 为本块实际占用的内联字节额度 */
    private static final class Block {

        private final String text;

        private final long inlineBytes;

        private Block(String text, long inlineBytes) {
            this.text = text;
            this.inlineBytes = inlineBytes;
        }
    }
}