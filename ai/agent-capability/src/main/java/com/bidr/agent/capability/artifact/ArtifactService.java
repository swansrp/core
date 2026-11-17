package com.bidr.agent.capability.artifact;

import com.bidr.blob.BlobStore;
import com.bidr.document.spi.ConvertStep;
import com.bidr.llm.agent.file.AgentArtifact;
import com.bidr.llm.agent.file.AgentArtifactGenerator;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.FileRef;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Title: ArtifactService
 * Description: {@link AgentArtifactGenerator} 的 gotenberg＋AgentFileStore 实现（P2a）——
 * 链路：Markdown → {@link MarkdownDocxRenderer} 出 docx 字节 →（format=pdf 时）
 * {@link ConvertStep#convertToPdf} 走 gotenberg LibreOffice 通道转 PDF →
 * {@link AgentFileStore#putArtifact} 落 {@code agent-artifact/{sessionId}/{fileId}.{format}} →
 * 现签预签名读址 → {@link AgentArtifact}。
 * <p>
 * <b>为什么 PDF 走 docx 中转而不是 Markdown→HTML→chromium</b>：tool/gotenberg 的
 * {@code GotenbergConvertClient} 契约只有 office→PDF 一条腿（{@code /forms/libreoffice/convert}，
 * 输入白名单 doc/docx/xls/xlsx/ppt/pptx、按魔数自证 PDF），HTML 不在其认领面；且 PDF 与 docx
 * 共用同一渲染源，两边版式一致（监理日志表格在两个格式里长得一样）。
 * <p>
 * 存储口径（2026-11-17 修订）：经 {@link AgentFileStore#putArtifact} 落 {@code agent-artifact/}
 * 独立命名空间（产物=耐用件，不进 temp/ 吃 ILM）——键形正源收在 BlobStore 实现一处；
 * <b>侧车照写</b>：业务行 file_url 的现签读址走 {@code AgentFileStore.get(sessionId, fileId)}，
 * 旧版「键可按 file_id+format 重构（无需侧车）」在 get() 回查口径下不成立（2026-11-17 实锤，
 * 本次一并修）。预签名有效期沿用存储实现固定口径（1 小时），过期由模型重新 generate_artifact 出新件。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
public class ArtifactService implements AgentArtifactGenerator {

    /** 支持的产物格式（小写） */
    public static final Set<String> SUPPORTED_FORMATS = Set.of("docx", "pdf");

    /** 展示文件名的最大长度（超出截断，防御超长 title 撑爆卡片与对象名） */
    private static final int MAX_TITLE_LENGTH = 80;

    private final ConvertStep convertStep;

    /** 产物存储端口（懒解析：装配相位与 run 时点解耦，缺席在 generate 时报人话错误） */
    private final Supplier<AgentFileStore> fileStore;

    public ArtifactService(ConvertStep convertStep, Supplier<AgentFileStore> fileStore) {
        this.convertStep = convertStep;
        this.fileStore = fileStore;
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
        AgentFileStore store = fileStore == null ? null : fileStore.get();
        if (store == null) {
            throw new IllegalStateException("产物存储端口缺席（AgentFileStore 未装配），无法落产物");
        }
        try {
            byte[] docx = MarkdownDocxRenderer.render(markdown);
            byte[] bytes = "pdf".equals(fmt) ? convertStep.convertToPdf("artifact.docx", docx) : docx;
            String name = artifactName(title, fmt);
            String mime = "pdf".equals(fmt)
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            FileRef ref = store.putArtifact(sessionId, name, mime, bytes);
            String url = store.presignedUrl(ref, BlobStore.DEFAULT_EXPIRE_SECONDS);
            log.info("会话产物已生成: session={}, format={}, name={}B, key={}", sessionId, fmt, bytes.length,
                    ref.key());
            return new AgentArtifact(ref.fileId(), ref.name(), ref.size(), ref.mime(), fmt, url);
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
}
