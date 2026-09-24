package com.bidr.llm.agent;

import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.constant.param.LlmParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;

/**
 * Title: AgentContextBudget
 * Description: L1 上下文预算治理五项阈值的取值单一出口（三级优先级，I9 兜底）：
 * <pre>option 正值 &gt; sys_config（LlmParam 运维灰度） &gt; Param 默认值（全关/保守默认）</pre>
 * Param 读取口径参照 DbAwareModelConfigProvider#dbValue：SysConfigCacheService 取不到
 * （启动早期无 Spring 上下文/参数未入库/值非数字）一律吞掉回落默认，绝不让取参击穿工具循环。
 * 纯决策逻辑收敛在包级 resolve(dbValue, override, enumDefault)，供单测直打
 *
 * @author Sharp
 * @since 2026/9/25
 */
@Slf4j
public final class AgentContextBudget {

    private AgentContextBudget() {
    }

    /** 工具结果入场卸载阈值（字）：0=关闭不卸载 */
    public static int offloadChars(int override) {
        return resolve(fromParam(LlmParam.AGENT_TOOL_RESULT_OFFLOAD_CHARS), override, enumDefault(LlmParam.AGENT_TOOL_RESULT_OFFLOAD_CHARS));
    }

    /** 上下文 token 预算（估算 token）：0=仅按条数裁窗（既有行为） */
    public static int tokenBudget(int override) {
        return resolve(fromParam(LlmParam.AGENT_CONTEXT_TOKEN_BUDGET), override, enumDefault(LlmParam.AGENT_CONTEXT_TOKEN_BUDGET));
    }

    /** 指针头尾预览合计字数 */
    public static int previewChars() {
        return resolveFloor(fromParam(LlmParam.AGENT_TOOL_RESULT_PREVIEW_CHARS), 0, enumDefault(LlmParam.AGENT_TOOL_RESULT_PREVIEW_CHARS));
    }

    /** 单会话回捞次数上限 */
    public static int recallMaxPerRun() {
        return resolveFloor(fromParam(LlmParam.AGENT_TOOL_RECALL_MAX_PER_RUN), 0, enumDefault(LlmParam.AGENT_TOOL_RECALL_MAX_PER_RUN));
    }

    /** 单次回捞返回字符上限 */
    public static int recallMaxChars() {
        return resolveFloor(fromParam(LlmParam.AGENT_TOOL_RECALL_MAX_CHARS), 0, enumDefault(LlmParam.AGENT_TOOL_RECALL_MAX_CHARS));
    }

    /**
     * 开关键三级取值纯函数（供单测直打）：override 正值最优先；否则解析 db 值——
     * null/空白回落枚举默认，非数字回落枚举默认（不抛），0 与负值一律视为关闭返回 0。
     * 仅用于 offloadChars/tokenBudget 两个开关键（0=关闭是设计本体，I9）
     */
    static int resolve(String dbValue, int override, int enumDefault) {
        if (override > 0) {
            return override;
        }
        if (dbValue == null || dbValue.trim().isEmpty()) {
            return Math.max(enumDefault, 0);
        }
        try {
            int v = Integer.parseInt(dbValue.trim());
            return v > 0 ? v : 0;
        } catch (NumberFormatException e) {
            return Math.max(enumDefault, 0);
        }
    }

    /**
     * 闸门键三级取值纯函数（供单测直打）：与 {@link #resolve} 唯一差别是 db 显式 0/负值不视为
     * 关闭而回落枚举默认（保守正值）——闸门键的「关」由上游 offloadOn 总开关决定，此处填 0 只是
     * 运维误配，回落保守默认防无限回捞/2 字预览失控（防御纵深）
     */
    static int resolveFloor(String dbValue, int override, int enumDefault) {
        if (override > 0) {
            return override;
        }
        if (dbValue == null || dbValue.trim().isEmpty()) {
            return Math.max(enumDefault, 0);
        }
        try {
            int v = Integer.parseInt(dbValue.trim());
            return v > 0 ? v : Math.max(enumDefault, 0);
        } catch (NumberFormatException e) {
            return Math.max(enumDefault, 0);
        }
    }

    /** 枚举 defaultValue 转 int（声明期写死，非法声明即编程错误；兜 0 防启动击穿） */
    private static int enumDefault(LlmParam p) {
        try {
            return Integer.parseInt(p.getDefaultValue());
        } catch (Exception e) {
            log.warn("LlmParam {} 默认值非数字，按 0 处理: {}", p.name(), e.getMessage());
            return 0;
        }
    }

    /** 读 sys_config 值（去空白）；服务未接入/参数未入库/读取异常一律返回 null 由 resolve 回落 */
    private static String fromParam(LlmParam p) {
        try {
            SysConfigCacheService service = BeanUtil.getBean(SysConfigCacheService.class);
            if (service == null) {
                return null;
            }
            String value = service.getSysConfigValue(p);
            return value == null ? null : value.trim();
        } catch (Exception e) {
            log.debug("读取系统参数 {} 失败，回落枚举默认: {}", p.name(), e.getMessage());
            return null;
        }
    }
}
