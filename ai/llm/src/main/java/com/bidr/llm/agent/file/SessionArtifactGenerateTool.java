package com.bidr.llm.agent.file;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.session.AgentEvent;
import com.bidr.llm.agent.session.AgentSessionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

/**
 * Title: SessionArtifactGenerateTool
 * Description: 会话受限的产物生成工具（P2a）——模型把<b>自己上下文里已有</b>的 Markdown 正文
 * 经 {@code content} 参数直传（不从 DB 反查：save_supervision_log 落库的是同一份正文，
 * 模型就是作者），本工具经 {@link AgentArtifactGenerator} 端口转 docx/pdf 落对象存储、
 * 追加 {@link AgentEvent#ARTIFACT} 事件（前端渲染下载卡片），并把预签名下载链接回给模型。
 * <p>
 * 定位口径：模型先 save_supervision_log 落库 → 再 generate_artifact(format=docx/pdf) 出下载件；
 * 工具本身不落业务库、不改会话状态，只做「Markdown → 文件 → 事件」的产物通道。
 * 端口缺席（gotenberg 腿/BlobStore 未装配）统一回「产物生成未启用」人话文本，不抛异常
 * （工具语义与 {@link SessionAttachmentReadTool} 一致）；转换/存储失败同样转人话文本。
 * <p>
 * 任务作用域：由 {@code AbstractToolLoopAgent#frameworkTools} 按会话新建，持有会话标识；
 * 事件经 {@code AgentSessionContext#emit} 落事件流（SSE 与轮询两路共用同一正源）。
 *
 * @author Sharp
 * @since 2026/11/13
 */
@Slf4j
public class SessionArtifactGenerateTool {

    /** 工具名（@Tool name 显式声明，e2e/能力包登记按此名核对） */
    public static final String TOOL_NAME = "generate_artifact";

    private final String sessionId;

    private final AgentSessionContext ctx;

    private final AgentArtifactGenerator generator;

    /**
     * 任务作用域构造：按当前会话新建；生成端口缺席时保留 null，
     * 调用期回明确错误文本（不在构造期抛，避免工具面装配被一个可选能力击穿）
     */
    public SessionArtifactGenerateTool(AgentSessionContext ctx) {
        this(ctx == null ? null : ctx.getSessionId(), ctx, beanOrNull(AgentArtifactGenerator.class));
    }

    /** 可注入构造（测试/自定义装配用）：显式给出会话与依赖 */
    SessionArtifactGenerateTool(String sessionId, AgentSessionContext ctx, AgentArtifactGenerator generator) {
        this.sessionId = sessionId;
        this.ctx = ctx;
        this.generator = generator;
    }

    /**
     * 生成本会话产物文件：把要导出的 Markdown 全文转成 docx/pdf 落存储，回下载链接并给
     * 前端发下载卡片事件。正文以本参数为准（生成后若又修订，应先重新 save_supervision_log
     * 再用新正文调用本工具）。
     *
     * @param content 要导出的 Markdown 正文全文（与 save_supervision_log 的 summary 同源；
     *                模型直接把当前成品全文传出，不在 DB 反查）
     * @param format  产物格式：docx 或 pdf（缺省按 docx）
     * @param title   展示用文件名（可空，如「监理日志-2026-11-13」；不必带扩展名）
     * @return 生成结果文本（含文件名与下载链接）；失败回人话错误文本（不抛）
     */
    @Tool(name = TOOL_NAME, value = "把一段 Markdown 正文转成可下载的文件（Word docx 或 PDF）供用户下载，"
            + "并给前端发下载卡片。典型用途：save_supervision_log 落库后，把同一份日志正文导出为 docx。"
            + "content 传要导出的 Markdown 全文（就是刚生成/修订的成品，不要截断）；format 传 docx 或 pdf；"
            + "title 传展示文件名（如「监理日志-2026-11-13」，可不带扩展名）。返回文本里的下载链接请原样告知用户")
    public String generateArtifact(
            @P(name = "content", value = "要导出的 Markdown 正文全文（与 save_supervision_log 的 summary 同源，禁止截断）") String content,
            @P(name = "format", value = "产物格式：docx 或 pdf（不传按 docx）") String format,
            @P(name = "title", value = "展示用文件名（可空，不必带扩展名，如「监理日志-2026-11-13」）") String title) {
        if (content == null || content.trim().isEmpty()) {
            return "拒绝：content 不能为空（要导出的 Markdown 正文全文），补齐后重试";
        }
        if (generator == null) {
            return "产物生成未启用：当前宿主未装配产物生成服务（gotenberg 转换腿或对象存储未配置）";
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "产物生成未启用：当前会话标识缺失";
        }
        String fmt = format == null || format.trim().isEmpty() ? "docx" : format.trim();
        AgentArtifact artifact;
        try {
            artifact = generator.generate(sessionId, content, fmt, title);
        } catch (IllegalArgumentException e) {
            // 非法格式等入参问题：文案面向模型可自行纠正（实现侧的拒绝信息即人话）
            log.info("产物生成入参被拒绝, sessionId={}, format={}, error={}", sessionId, fmt, e.getMessage());
            return "拒绝：" + e.getMessage();
        } catch (Exception e) {
            log.warn("产物生成失败, sessionId={}, format={}, error={}", sessionId, fmt, e.getMessage());
            return "产物生成失败（转换服务或存储暂不可用），请稍后重试";
        }
        if (artifact == null || artifact.url() == null || artifact.url().trim().isEmpty()) {
            log.warn("产物生成未取得下载链接, sessionId={}, format={}", sessionId, fmt);
            return "产物生成失败，请稍后重试";
        }
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
        log.info("[LLM工具] 会话 '{}' generate_artifact: format={}, name={}, size={}B, eventSeq={}",
                sessionId, artifact.format(), artifact.name(), artifact.size(), seq);
        return "文件已生成：" + artifact.name() + "（格式 " + artifact.format()
                + "，大小 " + artifact.size() + " 字节），下载链接：" + artifact.url();
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
