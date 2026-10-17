package com.bidr.llm.flow;

/**
 * Title: FlowTraceRetentionProvider
 * Description: 轨迹保留天数 SPI——业务模块接自己的配置中心（如系统参数），
 * 实时读取改后即生效；无实现的应用回落默认 10 天。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public interface FlowTraceRetentionProvider {

    /**
     * 轨迹保留天数（天，&le;0 视为非法由记录器回落默认值）
     */
    int retentionDays();
}
