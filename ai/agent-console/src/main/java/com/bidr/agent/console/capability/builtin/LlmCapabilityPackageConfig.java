package com.bidr.agent.console.capability.builtin;

import com.bidr.agent.console.capability.AgentCapabilityPackage;
import com.bidr.agent.console.capability.CapabilityTag;
import com.bidr.llm.agent.AgentToolRecall;
import com.bidr.llm.agent.tools.ExternalRuntimeTools;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Title: LlmCapabilityPackageConfig
 * Description: core/llm 侧 6 个能力包（共 12 只纯通用工具）的登记件。
 * <p>
 * 登记住在 console 侧、<b>工具代码一行不改</b>：依赖方向是 agent-console → llm（引用其注册名常量），
 * llm 不感知本模块。这样"框架自带的通用能力"先进目录，业务模块照同一 SPI 追加自己的包
 * （如墨枢 writer 把 suggest_next_steps 登记在自己的 next-steps 包——类已下沉 core，
 * 谁挂谁登记，避免与本目录重名）。
 * <p>
 * 🔴 工具名一律取自框架常量或 {@code @Tool} 方法的 camelCase 注册名；
 * {@link LlmCapabilityPackageConfigTest} 逐类反射比对，防"手写名字静默失配"。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Configuration
public class LlmCapabilityPackageConfig {

    /** 模块标识（清单分组用，非 Spring 模块名） */
    static final String MODULE = "llm";

    private static Set<CapabilityTag> tags(CapabilityTag... tags) {
        return tags.length == 0 ? Collections.emptySet() : EnumSet.of(tags[0], tags);
    }

    /** 计划台账：提交清单 / 标记执行中 / 挑勾完成（只动会话运行态，不碰业务数据） */
    @Bean
    public AgentCapabilityPackage planCapabilityPackage() {
        return AgentCapabilityPackage.of("plan", "计划台账", MODULE,
                "把本次任务拆成待办清单并逐条推进：submitPlan → startPlanItem → donePlanItem。"
                        + "只写会话内的计划状态。",
                tags(CapabilityTag.SESSION),
                "submitPlan", "startPlanItem", "donePlanItem");
    }

    /** 向用户提问并等待选择（决策缺口就地闭合，不编造） */
    @Bean
    public AgentCapabilityPackage askUserCapabilityPackage() {
        return AgentCapabilityPackage.of("ask-user", "向用户提问", MODULE,
                "遇到无法从已获取信息中确定的业务决策时，提问并等待用户选择。",
                tags(CapabilityTag.SESSION),
                "askUser");
    }

    /** 结果回捞：按 tool_call_id 取回被滑窗摘要掉的结果原文 */
    @Bean
    public AgentCapabilityPackage toolRecallCapabilityPackage() {
        return AgentCapabilityPackage.of("tool-recall", "工具结果回捞", MODULE,
                "上下文里被摘要收口的工具结果，可按句柄取回原文。只读。",
                tags(CapabilityTag.READ),
                AgentToolRecall.TOOL_NAME);
    }

    /** 外部运行时任务：派发 / 有界等待 / 查进度 / 取结论 / 取消 */
    @Bean
    public AgentCapabilityPackage externalTaskCapabilityPackage() {
        return AgentCapabilityPackage.of("external-task", "外部运行时任务", MODULE,
                "把需要真实执行环境（跑命令/改文件/长耗时）的子任务派给外部 agent runtime。"
                        + "副作用由那侧决定，不属于本会话的读写面。",
                tags(CapabilityTag.EXTERNAL_EFFECT),
                ExternalRuntimeTools.TOOL_SUBMIT, ExternalRuntimeTools.TOOL_WAIT,
                ExternalRuntimeTools.TOOL_QUERY, ExternalRuntimeTools.TOOL_READ,
                ExternalRuntimeTools.TOOL_CANCEL);
    }

    /** 调用一条已编排流程（同步链路） */
    @Bean
    public AgentCapabilityPackage runFlowCapabilityPackage() {
        return AgentCapabilityPackage.of("run-flow", "调用编排流程", MODULE,
                "按 flowKey 调用一条已编排好的流程并取其输出；流程内部做什么由编排侧决定。",
                tags(CapabilityTag.EXTERNAL_EFFECT),
                "runFlow");
    }

    /**
     * 读文件（read_file，2026-10-18）：http(s)/本地路径 → Markdown 正文，
     * 长文件带 offset 分页续读；能力面即 FileMarkdownService 门面（外部转录腿挂不挂对模型不可见）
     */
    @Bean
    public AgentCapabilityPackage fileReadCapabilityPackage() {
        return AgentCapabilityPackage.of("file-read", "读文件", MODULE,
                "把一份文件读取为 Markdown 正文（pdf/图片/office/文本），长文件可分页续读。只读。",
                tags(CapabilityTag.READ),
                "readFile");
    }
}
