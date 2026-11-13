package com.bidr.agent.capability.artifact;

import com.bidr.blob.BlobStore;
import com.bidr.document.spi.ConvertStep;
import com.bidr.llm.agent.file.AgentArtifact;
import com.bidr.llm.agent.file.AgentArtifactGenerator;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Title: ArtifactService
 * Description: {@link AgentArtifactGenerator} 的 gotenberg＋BlobStore 实现（P2a）——
 * 链路：Markdown → {@link MarkdownDocxRenderer} 出 docx 字节 →（format=pdf 时）
 * {@link ConvertStep#convertToPdf} 走 gotenberg LibreOffice 通道转 PDF → BlobStore 落
 * {@code agent-artifact/{sessionId}/{fileId}.{format}} → 现签预签名读址 → {@link AgentArtifact}。
 * <p>
 * <b>为什么 PDF 走 docx 中转而不是 Markdown→HTML→chromium</b>：tool/gotenberg 的
 * {@code GotenbergConvertClient} 契约只有 office→PDF 一条腿（{@code /forms/libreoffice/convert}，
 * 输入白名单 doc/docx/xls/xlsx/ppt/pptx、按魔数自证 PDF），HTML 不在其认领面；且 PDF 与 docx
 * 共用同一渲染源，两边版式一致（监理日志表格在两个格式里长得一样）。
 * <p>
 * 存储口径：不走 {@code AgentFileStore}（附件语义：run 线程无租户上下文，归属段会退化成
 * anonymous；且产物不是模型可读附件）——直用 BlobStore 按确定性键落 {@code agent-artifact/}
 * 独立命名空间，fileId 为 32 位 hex（满足 FileRef 同款 {@code [A-Za-z0-9_-]{1,128}} 字符集契约），
 * 事件流 payload 即元数据正源，键可按 file_id+format 重构（无需侧车）。
 * 预签名有效期沿用 BlobStore 固定口径（1 小时），过期由模型重新 generate_artifact 出新件。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
public class ArtifactService implements AgentArtifactGenerator {

    /** 产物对象键前缀（agent-session 之外的新命名空间：产物不是附件，读写面/清单位都不同） */
    public static final String KEY_PREFIX = "agent-artifact/";

    /** 支持的产物格式（小写） */
    public static final Set<String> SUPPORTED_FORMATS = Set.of("docx", "pdf");

    /** 展示文件名的最大长度（超出截断，防御超长 title 撑爆卡片与对象名） */
    private static final int MAX_TITLE_LENGTH = 80;

    private final ConvertStep convertStep;

    private final BlobStore blobStore;

    public ArtifactService(ConvertStep convertStep, BlobStore blobStore) {
        this.convertStep = convertStep;
        this.blobStore = blobStore;
    }

    @Override
    public AgentArtifact generate(String sessionId, String markdown, String format, String title) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        if (markdown == null || markdown.trim().isEmpty()) {
            throw new IllegalArgumentException("markdown 正文不能为空");
        }
        String fmt = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_FORMATS.contains(fmt)) {
            throw new IllegalArgumentException("暂不支持的产物格式：" + format + "（支持 docx/pdf）");
        }
        try {
            byte[] docx = MarkdownDocxRenderer.render(markdown);
            byte[] bytes = "pdf".equals(fmt) ? convertStep.convertToPdf("artifact.docx", docx) : docx;
            String name = artifactName(title, fmt);
            String mime = "pdf".equals(fmt)
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            String fileId = UUID.randomUUID().toString().replace("-", "");
            String key = KEY_PREFIX + safeSegment(sessionId.trim()) + "/" + fileId + "." + fmt;
            blobStore.put(key, bytes, mime);
            String url = blobStore.presign(key);
            log.info("会话产物已生成: session={}, format={}, name={}B, key={}", sessionId, fmt, bytes.length, key);
            return new AgentArtifact(fileId, name, bytes.length, mime, fmt, url);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            // 转换腿/存储失败原样上抛（DocumentConvertException 已带 retryable 语义），
            // 工具层统一转人话错误文本；这里不吞栈，保留排障线索
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new IllegalStateException("产物生成失败：" + e.getMessage(), e);
        }
    }

    /** 展示文件名：title 清洗（取路径末段、去存储非法字符、限长），空则按时间戳回落 */
    private static String artifactName(String title, String fmt) {
        String base = title == null ? "" : title.trim();
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1).trim();
        }
        // 去掉误带的扩展名与 Word 非法字符，避免下载时浏览器二次改名
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot).trim();
        }
        base = base.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (base.isEmpty()) {
            base = "artifact-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        }
        if (base.length() > MAX_TITLE_LENGTH) {
            base = base.substring(0, MAX_TITLE_LENGTH);
        }
        return base + "." + fmt;
    }

    /** 对象键段清洗：只留安全字符（与 BlobStoreAgentFileStore#safeSegment 同口径） */
    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._\\-]", "_");
    }
}
