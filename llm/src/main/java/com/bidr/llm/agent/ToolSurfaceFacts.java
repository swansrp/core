package com.bidr.llm.agent;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Title: ToolSurfaceFacts
 * Description: 工具面定形那一刻框架**确实知道**的事实（M14 棒2 装配单的入参）。
 * 只装框架侧事实：会话身份、发起人、本次挂上的工具名清单、原始 payload。
 * 业务语义（哪个 feature、哪个空间、注入了哪些技能、哪档模型）框架一概不认，
 * 由消费方按 {@code payload} 自行解读——payload 原样透传，不裁剪不改写
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Data
public class ToolSurfaceFacts {

    /** 框架会话标识 */
    private String sessionId;

    /** agent 运行时注册键 */
    private String agentKey;

    /** agent 定义层的技能归属码（评价维度，非能力绑定） */
    private String skillCode;

    /** agent 展示名 */
    private String displayName;

    /** 发起人（框架 OperatorResolver 口径） */
    private String operator;

    /** 本次会话手里有哪些工具（＝发给模型的工具名清单，含引擎按上下文预算追加的回捞工具） */
    private List<String> toolNames;

    /** 发起时的原始 payload（只读用途，消费方自取键位） */
    private Map<String, Object> payload;
}
