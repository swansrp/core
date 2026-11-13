package com.bidr.llm.agent;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.file.AgentFileStore;
import com.bidr.llm.agent.file.AttachmentContextAssembler;
import com.bidr.llm.agent.file.FileRef;
import com.bidr.llm.agent.file.SessionAttachmentReadTool;
import com.bidr.llm.agent.session.AgentSessionContext;
import com.bidr.llm.usage.ModelCallContexts;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Title: AbstractToolLoopAgent
 * Description: 简单工具循环 agent 的模板基类（泛型任务状态 S）——新业务只填钩子不写
 * {@link AutonomousAgentDefinition#start}：{@link #prepare} 做校验/抢锁/备任务态，
 * model/systemPrompt/userPrompt/tools 四要素各一钩，基类统一驱动 {@link ToolAgentRunner#run}
 * 并强制收口契约（停止→InterruptedException→会话 STOPPED、终态写 {@link AgentSessionContext#setSummary}、
 * loopListener 接线、{@link #release} 异常路径也保证资源释放），想写错都难。
 * <p>选型口径：单次 LLM 工具循环 → 继承本类；多阶段/桥接存量任务体（进度记录/心跳等复合形态）
 * → 直接实现 {@link AutonomousAgentDefinition}。定义 Bean 为 Spring 单例：钩子间禁放会话级字段，
 * 会话状态一律走 S 经参传递。
 *
 * @param <S> 会话级任务状态类型（prepare 产物，如连接/上下文封装；无状态需求以 Void 实例化并返回 null）
 * @author Sharp
 * @since 2026/8/20
 */
@Slf4j
public abstract class AbstractToolLoopAgent<S> implements AutonomousAgentDefinition {

    /** 通用工具循环引擎（无状态，与业务侧同口径直接实例化，不走 Bean 装配） */
    private final ToolAgentRunner runner = new ToolAgentRunner();

    /**
     * 附件能力总开关（{@code llm.agent.file-tools.enabled}，缺省开）：
     * 关＝<b>整体回退</b>——{@link #frameworkTools} 恒空，且 {@link #attachmentContext} 直接返回空串
     * （不读附件、不拼提示词），逐轮注入（{@link AgentSessionContext#pendingUserText()}）
     * 与 {@code /agents} 能力位同样按该键关闭；提示词与改造前逐字节一致
     */
    @Value("${llm.agent.file-tools.enabled:true}")
    private boolean fileToolsEnabled = true;

    /**
     * 小文本附件直接注入正文的字节上限（{@code llm.agent.files.inline-text-max-bytes}，
     * 缺省 64KB）：白名单文本 MIME 且不超过此上限的附件按 UTF-8 解码后随提示词直接给出内容；
     * 其余走会话受限工具 {@code read_attachment}（按 file_id 读取，见 {@link SessionAttachmentReadTool}）
     */
    @Value("${llm.agent.files.inline-text-max-bytes:65536}")
    private long inlineTextMaxBytes = 65536L;

    /**
     * 单批附件内联正文的总字节上限（{@code llm.agent.files.inline-total-max-bytes}，缺省 128KB）：
     * 多附件累计超过此上限时按剩余额度截断并标注「…（已截断）」，防止一次请求灌爆模型上下文
     */
    @Value("${llm.agent.files.inline-total-max-bytes:131072}")
    private long inlineTotalMaxBytes = 131072L;

    /**
     * 循环前置准备：payload 校验（缺参直接抛出，会话层收口 FAILED）、抢锁、开连接、构建任务态。
     * 默认无状态（null）；异常向外抛出由会话层收口
     */
    protected S prepare(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        return null;
    }

    /** 本会话使用的业务模型 */
    protected abstract ChatModel model(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /** 系统提示词（角色与约束） */
    protected abstract String systemPrompt(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /** 用户提示词（任务目标 + 上下文，可运行期拼装） */
    protected abstract String userPrompt(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /** 工具对象清单（@Tool 注解方法，任务作用域实例，可持有 state 内的连接/上下文等状态） */
    protected abstract List<Object> tools(S state, AgentSessionContext ctx, Map<String, Object> payload);

    /**
     * 函数式工具声明清单（默认空＝与改造前逐字节一致）。
     * <p>覆写它即可把工具声明成"名字＋说明＋参数 schema＋一个处理器"，不必是 {@code @Tool} 反射方法；
     * 三件声明字段都是可序列化的值，所以将来能从台账行里读出来直接喂进来（执行仍在进程内）。
     * 与反射面的合并、收窄、同名裁决都在 {@link ToolAgentRunner} 一处收口，本钩子不做任何裁决。
     */
    protected List<FunctionalTool> functionalTools(S state, AgentSessionContext ctx,
                                                   Map<String, Object> payload) {
        return Collections.emptyList();
    }

    /**
     * 框架工具钩子：默认并入<b>会话受限</b>附件读取工具 {@link SessionAttachmentReadTool}
     * （工具名 {@code read_attachment}，参数只有 file_id/max_chars/offset），
     * {@link #start} 里与 {@link #tools} 合并进同一工具面。
     * <p>
     * <b>安全口径（C-1）</b>：框架不再默认并入通用 {@code read_file}（{@code FileMarkdownTool}）——
     * 它接受服务器本地路径与任意 http(s) URL，会把任意文件读取/SSRF 能力默认发给所有 agent。
     * 业务若确有"读任意地址"的明确需求，在 {@link #tools} 里自行挂载 {@code FileMarkdownTool}。
     * <p>
     * 关闭方式两档：宿主总开关 {@code llm.agent.file-tools.enabled=false}（缺省 true，整体回退：
     * 工具面、附件上下文注入、能力位一起关闭）；单个 agent 不要附件读取工具时覆写本方法返回空列表
     * （per-agent 让位，仅工具面；附件上下文注入是否随之关闭由业务覆写 {@link #attachmentContext} 决定）。
     * 存储/文档能力缺席时仍返回本工具（调用期回明确错误文本），不阻断工具面装配。
     */
    protected List<Object> frameworkTools(S state, AgentSessionContext ctx, Map<String, Object> payload) {
        if (!fileToolsEnabled || ctx == null) {
            return Collections.emptyList();
        }
        List<Object> tools = new ArrayList<>(1);
        tools.add(new SessionAttachmentReadTool(ctx));
        return tools;
    }

    // ---- 会话附件 → 模型可见上下文 ----

    /**
     * 会话附件前置说明（拼在业务 {@link #userPrompt} 之前，让模型拿到 file_id 或原文）：
     * 拼装规则见 {@link AttachmentContextAssembler}（白名单文本小件直出原文；其余给
     * {@code file_id} 指令，由会话受限工具 {@code read_attachment} 按需读取）。
     * 无附件、存储端口缺席或全部附件定位失败时返回空串——调用方不拼，提示词与改造前逐字节一致。
     * <p>
     * 本钩子同时是**首轮投递**的记账口：拼出非空前缀后把本批附件标记为已投递，
     * 运行期逐轮增量注入（{@link AgentSessionContext#pendingUserText()}）据此做差，
     * 同一附件不会在后续轮次重复注入。
     */
    protected String attachmentContext(AgentSessionContext ctx) {
        if (!fileToolsEnabled) {
            // I-r2-1 整体回退：开关关时不读附件事件/不读正文/不拼提示词，与改造前逐字节一致
            return "";
        }
        if (ctx == null) {
            return "";
        }
        List<FileRef> attachments;
        try {
            attachments = ctx.attachedFiles();
        } catch (Exception e) {
            log.warn("会话附件清单读取失败（本次不注入附件上下文）, sessionId={}, error={}",
                    ctx.getSessionId(), e.getMessage());
            return "";
        }
        if (attachments == null || attachments.isEmpty()) {
            return "";
        }
        AgentFileStore fileStore = beanOrNull(AgentFileStore.class);
        if (fileStore == null) {
            // 宿主未装配附件能力：上传事件可能存在（如旧数据/外部写入），但没有读取通道
            return "";
        }
        String text = AttachmentContextAssembler.assemble(fileStore, ctx.getSessionId(),
                attachments, inlineTextMaxBytes, inlineTotalMaxBytes);
        if (!text.isEmpty()) {
            // 首轮已进提示词的附件记投递（逐轮增量注入侧做差用，防重复注入）
            ctx.markAttachmentsDelivered(attachments);
        }
        return text;
    }

    /** 轮次/滑窗参数（默认 30/60，见 {@link AgentLoopOptions}） */
    protected AgentLoopOptions loopOptions() {
        return new AgentLoopOptions();
    }

    /** 结论摘要（FINISHED 时前端展示）：默认取循环最终文本，可覆写定制 */
    protected String summary(AgentLoopResult result, S state, AgentSessionContext ctx, Map<String, Object> payload) {
        return result.getText();
    }

    /** 资源释放（prepare 开连接/抢锁等时覆写；异常路径也保证执行）；默认空 */
    protected void release(S state) {
    }

    /** 模板执行体：final 禁覆写——停止映射/摘要写入/钩子接线/资源释放等收口契约由基类保证 */
    @Override
    public final void start(AgentSessionContext ctx, Map<String, Object> payload) throws Exception {
        S state = prepare(ctx, payload);
        // 用量计量的身份锚点（M14 棒5）：整场循环期间本线程带会话身份，模型层收口时现读；
        // 循环里的同步调用发生在 llm-call 子线程，那一跳由 ToolAgentRunner 显式把快照带过去。
        // 线程池会复用线程，clear 必须在 finally——漏一次就是把上一场的身份写给下一场
        ModelCallContexts.set(ctx.usageIdentity());
        try {
            // 业务工具 + 框架工具合并：重复/同名的裁决在 ToolAgentRunner 一处收口，本类只做并集
            List<Object> mergedTools = new ArrayList<>();
            List<Object> businessTools = tools(state, ctx, payload);
            if (businessTools != null) {
                mergedTools.addAll(businessTools);
            }
            List<Object> mergedFrameworkTools = frameworkTools(state, ctx, payload);
            if (mergedFrameworkTools != null) {
                mergedTools.addAll(mergedFrameworkTools);
            }
            ChatModel model = model(state, ctx, payload);
            String systemPrompt = systemPrompt(state, ctx, payload);
            String businessPrompt = userPrompt(state, ctx, payload);
            // 附件前置说明：业务提示词构建后、进循环前拼入（无附件/无存储端口为空串，
            // 拼接结果与改造前逐字节一致）
            String attachmentPrefix = attachmentContext(ctx);
            String prompt = attachmentPrefix.isEmpty() ? businessPrompt
                    : attachmentPrefix + "\n\n" + (businessPrompt == null ? "" : businessPrompt);
            AgentLoopResult result = runner.run(model, systemPrompt, prompt, mergedTools,
                    functionalTools(state, ctx, payload), loopOptions(), ctx.loopListener());
            if (result.isStopped()) {
                // 与直接实现接口同口径：会话层捕获 InterruptedException 收口 STOPPED
                throw new InterruptedException("用户停止");
            }
            ctx.setSummary(summary(result, state, ctx, payload));
        } finally {
            ModelCallContexts.clear();
            release(state);
        }
    }

    /**
     * 安全取 Bean（m-4）：缺席 → null；多 Bean/解析异常（{@link BeansException} 系）
     * 一律视为能力不可用，不把 {@code NoUniqueBeanDefinitionException} 抛进会话启动链
     */
    private static <T> T beanOrNull(Class<T> type) {
        try {
            return BeanUtil.getBean(type);
        } catch (BeansException e) {
            log.debug("{} 解析不唯一/不可用，按能力缺席处理: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }
}
