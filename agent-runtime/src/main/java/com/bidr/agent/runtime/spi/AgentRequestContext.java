package com.bidr.agent.runtime.spi;

import com.bidr.agent.runtime.dao.entity.ChatSession;

import java.util.Map;

/**
 * Title: AgentRequestContext
 * Description: 业务扩展点——**本模块零业务引用的唯一缺口**（下沉 core 时业务只需实现本接口）。
 * <p>
 * 模块自行保证的不变式（不交给业务）：会话归属强校验（按 operator）、会话映射表读写、
 * 幂等键透传、帧原样透传。本接口只承载「这个项目怎么用」：默认 Agent、分组键口径、透传元数据、
 * 业务侧额外可见性约束与会话落成后的登记钩子。
 *
 * @author sharp
 * @since 2026/9/22
 */
public interface AgentRequestContext {

    /**
     * 当前登录人（会话归属依据；缺省取框架 AccountContext）
     */
    String currentOperator();

    /**
     * 会话绑定的 Agent 编码：请求未指定时由业务给默认值
     */
    String resolveAgentCode(String requestedAgentCode);

    /**
     * 平台分组键（业务维度，如项目号/单据号；平台侧按它过滤）
     */
    String resolveGroupKey(String requestedGroupKey, String businessType, String businessId);

    /**
     * 建会话时透传元数据（平台不解析，原样回带）
     */
    Map<String, Object> resolveMetadata(String businessType, String businessId);

    /**
     * 业务侧额外的可见性约束（如按业务单据鉴权）；缺省不额外限制
     */
    default void assertBusinessReadable(String businessType, String businessId) {
        // 缺省不限制
    }

    /**
     * 会话落成本地映射后的登记钩子（如写业务关联、审计）
     */
    default void onSessionCreated(ChatSession session) {
        // 缺省无动作
    }
}