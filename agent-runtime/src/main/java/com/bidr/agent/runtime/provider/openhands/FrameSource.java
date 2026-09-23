package com.bidr.agent.runtime.provider.openhands;

import java.io.Closeable;
import java.io.IOException;

/**
 * Title: FrameSource
 * Description: 上游 socket 帧的**阻塞读**抽象（实现是 {@link OpenHandsSocket}，单测用脚本化实现）。
 * <p>
 * 抽这一层的唯一目的：让 {@link OpenHandsTurnStream} 的帧映射可以拿**实测录制的帧序列**做单测，
 * 不必起上游容器。语义与 {@code RuntimeTurnLink.nextFrame} 对齐：{@code null} = 对端收流，
 * 抛异常 = 断流（**不是**取消）。
 *
 * @author sharp
 * @since 2026/9/23
 */
interface FrameSource extends Closeable {

    /**
     * 取下一帧原文
     *
     * @param timeoutMs 等待上限（≤0 = 无限等）
     * @return 帧 JSON；{@code null} = 对端已收流
     * @throws IOException 链路异常或等待超时
     */
    String nextFrame(long timeoutMs) throws IOException;

    /**
     * 收流（只断本次消费，不中断上游执行）
     */
    @Override
    void close();
}
