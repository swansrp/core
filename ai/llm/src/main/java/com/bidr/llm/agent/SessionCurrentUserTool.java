package com.bidr.llm.agent;

import com.bidr.kernel.utils.BeanUtil;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;

/**
 * Title: SessionCurrentUserTool
 * Description: 会话发起人身份工具（{@code current_user}，无参数）——模型可主动查询
 * 「当前用户是谁、属于哪个部门」，填表头人名栏位（填写人/编制人/申报人）时不必反问用户。
 * 框架只提供工具面与回落，数据来源走 {@link OperatorProfileResolver} SPI（宿主实现注入）：
 * <ul>
 *     <li>SPI 在场：报姓名＋部门（任一字段缺失按回落处理）；</li>
 *     <li>SPI 缺席/查无/解析异常：回落框架登记的发起人原文（operator 语义随宿主，
 *     可能是 userId 而非姓名），部门标注未知——工具永不抛，模型拿到的始终是可读文本。</li>
 * </ul>
 * 工具语义只读、无副作用、无存储依赖，因此<b>不参与</b> {@code llm.agent.file-tools.enabled}
 * 总开关（身份不是能力件）；任务作用域：由 {@code AbstractToolLoopAgent#frameworkTools}
 * 按会话新建，只持 operator 标量，无会话级可变字段。
 *
 * @author Sharp
 * @since 2026/11/18
 */
@Slf4j
public class SessionCurrentUserTool {

    /** 工具名（@Tool name 显式声明，装配核对按此名） */
    public static final String TOOL_NAME = "current_user";

    /** 框架登记的发起人原文（AgentSessionState.operator；空串统一按 anonymous 口径） */
    private final String operator;

    /** 档案 SPI（可缺席：宿主未实现时工具回落，不阻断工具面装配） */
    private final OperatorProfileResolver resolver;

    /**
     * 任务作用域构造：按当前会话发起人新建；SPI 经静态取 Bean 解析，
     * 缺席/多 Bean 一律视为未装配（不在构造期抛，同 {@code SessionAttachmentReadTool} 口径）
     */
    public SessionCurrentUserTool(String operator) {
        this(operator, resolveResolver());
    }

    /** 可注入构造（测试/自定义装配用）：显式给出发起人与档案 SPI */
    SessionCurrentUserTool(String operator, OperatorProfileResolver resolver) {
        this.operator = operator;
        this.resolver = resolver;
    }

    /**
     * 查询当前用户身份：姓名＋所属部门。无参数（发起人在会话建立时已定格，不接受模型代入）。
     *
     * @return 人话文本；失败也回可读回落（不抛）
     */
    @Tool(name = TOOL_NAME, value = "查询当前用户（本次会话的发起人）的姓名与所属部门。"
            + "需要填写填写人/编制人/申报人等身份栏位、或回答「我是谁/我在哪个部门」类问题时调用；"
            + "会话内身份不变，调用一次即可，不要反复调用")
    public String currentUser() {
        String who = operator == null || operator.trim().isEmpty() ? "anonymous" : operator.trim();
        if (resolver == null) {
            return fallback(who, "未知（宿主未提供人员档案）");
        }
        try {
            OperatorProfile profile = resolver.profileOf(who);
            if (profile == null) {
                return fallback(who, "未知（人员档案查无）");
            }
            String name = blank(profile.getName()) ? who : profile.getName().trim();
            String dept = blank(profile.getDepartment()) ? "未知" : profile.getDepartment().trim();
            return "当前用户姓名：" + name + "\n所属部门：" + dept;
        } catch (Exception e) {
            log.warn("current_user 档案解析失败（回落发起人原文）, operator={}, error={}", who, e.getMessage());
            return fallback(who, "未知（档案解析失败）");
        }
    }

    /** 回落文本：框架登记原文＋部门未知原因（模型可据此决定是否向用户确认身份） */
    private static String fallback(String who, String deptReason) {
        return "当前用户（框架登记）：" + who + "\n所属部门：" + deptReason;
    }

    private static boolean blank(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** 安全取 SPI Bean：缺席/多 Bean（BeansException 系）一律视为未装配 */
    private static OperatorProfileResolver resolveResolver() {
        try {
            return BeanUtil.getBean(OperatorProfileResolver.class);
        } catch (BeansException e) {
            log.debug("OperatorProfileResolver 解析不唯一/不可用，按未装配处理: {}", e.getMessage());
            return null;
        }
    }
}
