package com.bidr.llm.agent.sandbox;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.session.AgentSessionContext;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

import java.util.Collections;
import java.util.List;

/**
 * Title: SessionSandboxRunTool
 * Description: 会话受限的沙箱执行工具（棒3a）——模型把要在隔离环境跑的 shell 命令交给
 * {@link AgentSandbox} 端口执行（{@code ensure} 懒建沙箱＋预置种子 → {@code exec} 命令），
 * 回 exitCode＋合并输出。命令在<b>会话专属沙箱</b>内执行，不逃逸宿主；产物文件用
 * {@link SessionSandboxFetchTool}（{@code sandbox_fetch}）取回。
 * <p>
 * 端口缺席（沙箱底座未装配）统一回「沙箱未启用」人话文本，不抛异常（工具语义与
 * {@code SessionArtifactGenerateTool} 一致）；执行失败同样转人话文本。
 * <b>日志纪律</b>：命令可能含敏感内容（凭据/内网地址），{@code [LLM工具]} 行只打
 * 命令长度/exitCode/耗时，不打命令全文。
 * <p>
 * 任务作用域：由 {@code AbstractToolLoopAgent#frameworkTools} 按会话新建，持有会话标识；
 * 种子文件经构造器传入（agent 定义覆写 {@code sandboxSeedFiles} 钩子提供，默认空）。
 *
 * @author Sharp
 * @since 2026/11/14
 */
@Slf4j
public class SessionSandboxRunTool {

    /** 工具名（@Tool name 显式声明，e2e/能力包登记按此名核对） */
    public static final String TOOL_NAME = "sandbox_run";

    /** 缺省超时（秒） */
    public static final int DEFAULT_TIMEOUT_SECONDS = 120;

    /** 超时上限（秒）：更大的请求按上限执行（有界资源占用） */
    public static final int MAX_TIMEOUT_SECONDS = 600;

    private final String sessionId;

    private final AgentSandbox sandbox;

    private final List<SandboxSeedFile> seeds;

    /**
     * 任务作用域构造：按当前会话新建；沙箱端口缺席时保留 null，
     * 调用期回明确错误文本（不在构造期抛，避免工具面装配被一个可选能力击穿）
     *
     * @param ctx   会话上下文（只取会话标识；本工具无事件面，不持有引用）
     * @param seeds 沙箱种子文件（agent 定义给出；null 按空清单处理）
     */
    public SessionSandboxRunTool(AgentSessionContext ctx, List<SandboxSeedFile> seeds) {
        this(ctx == null ? null : ctx.getSessionId(), beanOrNull(AgentSandbox.class), seeds);
    }

    /** 可注入构造（测试/自定义装配用）：显式给出会话与依赖 */
    SessionSandboxRunTool(String sessionId, AgentSandbox sandbox, List<SandboxSeedFile> seeds) {
        this.sessionId = sessionId;
        this.sandbox = sandbox;
        this.seeds = seeds == null ? Collections.emptyList() : seeds;
    }

    /**
     * 在本会话沙箱内执行一条 shell 命令：首次调用会先建沙箱并预置 agent 定义的种子文件，
     * 之后按命令逐条执行。长任务请用 timeout_seconds 控制时限（缺省 120 秒、上限 600 秒），
     * 超时命令会被终止。
     *
     * @param command         要执行的 shell 命令（工作目录＝沙箱根，产出文件写到沙箱内）
     * @param timeoutSeconds  超时秒数（不传按 120，超过 600 按 600）
     * @return 执行结果文本（exitCode＋合并输出）；失败/超时回人话文本（不抛）
     */
    @Tool(name = TOOL_NAME, value = "在当前会话的隔离沙箱里执行一条 shell 命令（如跑脚本、处理数据文件），"
            + "并返回退出码与输出。沙箱内生成的文件可用 sandbox_fetch 取回。"
            + "command 传完整命令行；timeout_seconds 传超时秒数（不传按 120 秒，最大 600 秒），"
            + "超时的命令会被终止——请把长任务拆成小步分多次执行")
    public String runCommand(
            @P(name = "command", value = "要执行的 shell 命令全文（在会话沙箱内执行，支持多命令用 && 串联）") String command,
            @P(name = "timeout_seconds", value = "超时秒数：不传按 120，传超过 600 的值按 600 执行") Integer timeoutSeconds) {
        if (command == null || command.trim().isEmpty()) {
            return "拒绝：command 不能为空（要执行的 shell 命令全文），补齐后重试";
        }
        if (sandbox == null) {
            return "沙箱未启用：当前宿主未装配沙箱服务";
        }
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return "沙箱未启用：当前会话标识缺失";
        }
        int seconds = clampTimeout(timeoutSeconds);
        long timeoutMs = seconds * 1000L;
        long start = System.currentTimeMillis();
        try {
            sandbox.ensure(sessionId, seeds);
            SandboxExecResult result = sandbox.exec(sessionId, command, timeoutMs);
            long cost = System.currentTimeMillis() - start;
            log.info("[LLM工具] 会话 '{}' sandbox_run: cmdLen={}, timeoutMs={}, exitCode={}, timedOut={}, costMs={}",
                    sessionId, command.length(), timeoutMs, result.exitCode(), result.timedOut(), cost);
            if (result.timedOut()) {
                return "命令超时（" + seconds + " 秒）已终止，请缩小任务或分步执行";
            }
            String output = result.output() == null ? "" : result.output();
            return "exitCode=" + result.exitCode() + "\n" + output;
        } catch (Exception e) {
            log.warn("沙箱执行失败, sessionId={}, cmdLen={}, timeoutMs={}, error={}",
                    sessionId, command.length(), timeoutMs, e.getMessage());
            return "沙箱执行失败（沙箱服务暂不可用），请稍后重试";
        }
    }

    /** 超时限幅：null/非正数按缺省 120；超过上限按 600 */
    private static int clampTimeout(Integer timeoutSeconds) {
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        return Math.min(timeoutSeconds, MAX_TIMEOUT_SECONDS);
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
