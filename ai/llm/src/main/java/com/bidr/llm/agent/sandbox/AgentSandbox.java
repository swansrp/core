package com.bidr.llm.agent.sandbox;

import java.util.List;

/**
 * Title: AgentSandbox
 * Description: agent 会话沙箱端口（棒3a，llm 协议层零外围依赖）——"给模型一个隔离的
 * shell 执行环境"的协议最小单元：会话内执行命令（{@code sandbox_run}）与取回产出文件
 * （{@code sandbox_fetch}）两只会话受限工具都只认本接口，具体沙箱底座（容器/进程隔离等）
 * 由能力模块装配（棒3b 实现落位）；端口缺席＝沙箱能力不启用（{@code /agents} 不出
 * {@code exec.sandbox} 能力位，两只工具回「沙箱未启用」人话文本，不阻断工具面装配）。
 * <p>
 * 会话归属契约：所有方法第一参都是 {@code sessionId}——实现侧必须按会话隔离沙箱实例与
 * 文件系统（一个会话一个沙箱，跨会话不可见）；生命周期由实现侧管理（懒建 + 会话收口时
 * {@link #destroy}）。
 * <p>
 * 纯端口纪律（与 {@code AgentFileStore} 同口径）：本接口只依赖 java.*，不感知容器/云/
 * 存储等任何外围设施；{@link #fetch} 返回 {@code byte[]} 级原始字节，协议层不做内容推断。
 *
 * @author Sharp
 * @since 2026/11/14
 */
public interface AgentSandbox {

    /**
     * 懒建沙箱会话并预置种子文件（幂等：已建则只补缺的种子）
     *
     * @param sessionId 会话标识（沙箱隔离维度）
     * @param seeds     种子文件清单（可空/空＝不预置）；同一路径重复 ensure 以首次为准
     */
    void ensure(String sessionId, List<SandboxSeedFile> seeds);

    /**
     * 执行 shell 命令
     *
     * @param sessionId 会话标识
     * @param command   shell 命令行（实现侧负责在沙箱内执行，不逃逸到宿主）
     * @param timeoutMs 超时毫秒数（超时由实现侧终止进程并置 {@link SandboxExecResult#timedOut()}）
     * @return 执行结果（exitCode＋合并 stdout/stderr 截断后的 output＋timedOut）
     */
    SandboxExecResult exec(String sessionId, String command, long timeoutMs);

    /**
     * 读回沙箱内相对路径文件字节
     *
     * @param sessionId 会话标识
     * @param relPath   沙箱内相对路径（须相对；路径穿越校验由实现侧负责，非法路径抛
     *                  {@link IllegalArgumentException}）
     * @return 文件字节；不存在返回 null 或抛人话异常（实现侧口径，工具层统一转文本）
     */
    byte[] fetch(String sessionId, String relPath);

    /**
     * 销毁沙箱会话（会话收口时调；幂等：已销毁/不存在时静默）
     *
     * @param sessionId 会话标识
     */
    void destroy(String sessionId);
}
