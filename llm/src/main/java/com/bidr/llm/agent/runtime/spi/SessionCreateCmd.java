package com.bidr.llm.agent.runtime.spi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Title: SessionCreateCmd
 * Description: 建会话命令（**领域语义，不含上游字段名**）：由各 provider 自行拼成上游请求体
 * （agent-system 是 snake_case 的 agent_code/name/group_key/metadata；OpenHands 是 workspace+profile 那一套）。
 *
 * @author sharp
 * @since 2026/9/23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionCreateCmd {

    /**
     * 绑定的 Agent 编码（已由业务扩展点解析过，非空）
     */
    private String agentCode;

    /**
     * 会话标题（可空；上游侧创建即冻结，后续标题由本地维护）
     */
    private String title;

    /**
     * 业务分组键（可空）
     */
    private String groupKey;

    /**
     * 透传元数据（可空；上游不解析）
     */
    private Map<String, Object> metadata;

    /**
     * 业务类型/主键（供需要业务绑定的实现使用，如 OpenHands 的 working_dir 规划）
     */
    private String businessType;
    private String businessId;
}