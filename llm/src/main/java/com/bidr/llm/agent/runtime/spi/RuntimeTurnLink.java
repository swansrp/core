package com.bidr.llm.agent.runtime.spi;

import java.io.Closeable;

/**
 * Title: RuntimeTurnLink
 * Description: 一个轮次的一次消费链（建轮即流或挂流）。
 * <p>
 * 由 relay 在泵线程里独占消费：{@link #nextFrame()} 阻塞取下一帧；返回 {@code null} 表示上游正常收流；
 * 抛异常表示断流/失败（relay 会收流并让前端走挂流恢复）。
 * <p>
 * ⚠️ {@link #close()} 语义 = **只停止本次消费**，绝不取消上游轮次（断流 ≠ 取消）。
 *
 * @author sharp
 * @since 2026/9/23
 */
public interface RuntimeTurnLink extends Closeable {

    /**
     * 取下一帧，形如 {@code {"type":"text.delta","session_id":…,"message_id":…,"data":{…}}}
     *
     * @return 帧 JSON 原文；{@code null} = 已收流
     * @throws Exception 读取失败/链路断开（按断流处理）
     */
    String nextFrame() throws Exception;

    /**
     * 收流并释放连接（不取消轮次）
     */
    @Override
    void close();
}