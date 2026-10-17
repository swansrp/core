package com.bidr.llm.flow;

/**
 * Title: FlowExecutionListener
 * Description: 流程执行监听器 SPI——链路收口回调（正常结束与失败同路径），
 * 业务模块借此做链路外的持久化收尾（如补写助手回复回传消息标识），引擎不感知具体业务。
 *
 * @author Sharp
 * @since 2026/8/16
 */
public interface FlowExecutionListener {

    /**
     * 链路收口：error 为 null 表示成功，非 null 为失败原因
     * （成功取响应变量从 ctx.getOutput()，如 answer/chartSpec）
     */
    void onFinish(FlowContext context, String error);
}
