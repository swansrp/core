package com.bidr.llm.agent;

/**
 * Title: OperatorResolver
 * Description: 当前访问人解析 SPI——llm 基础框架不依赖 authorization，
 * 会话发起人与流程轨迹的访问人隔离经业务侧注册实现注入
 * （如 insight 侧实现返回 AccountContext.getDisplayName()）。
 * 无实现时回落 anonymous（与轨迹记录的免登回落同区，调试链路不阻断）
 *
 * @author Sharp
 * @since 2026/8/20
 */
public interface OperatorResolver {

    /**
     * 当前登录人显示名（空/未登录返回 null 或空串均可，调用方回落 anonymous）
     */
    String currentOperator();
}
