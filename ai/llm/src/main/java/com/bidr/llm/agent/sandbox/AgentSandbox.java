package com.bidr.llm.agent.sandbox;

import java.util.List;

/**
 * Title: AgentSandbox
 * Description: agent 会话沙箱端口（棒3a，llm 协议层零外围依赖）——"给模型一个隔离的
 * shell 执行环境"的协议最小单元：会话内执行命令（{@code sandbox_run}）、取回产出文件
 * （{@code sandbox_fetch}）与放入原件（{@code sandbox_put_attachment}）三只会话受限工具都只认
 * 本接口，具体沙箱底座（容器/进程隔离等）由能力模块装配（棒3b 实现落位）；端口缺席＝沙箱能力
 * 不启用（{@code /agents} 不出 {@code exec.sandbox} 能力位，三只工具回「沙箱未启用」人话文本，
 * 不阻断工具面装配）。
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
     * 懒建沙箱会话并预置种子文件（幂等：会话已建不重建；种子按清单全量重传，同路径覆盖＝最新为准）
     *
     * @param sessionId 会话标识（沙箱隔离维度）
     * @param seeds     种子文件清单（可空/空＝不预置）；同一路径重复 ensure 覆盖重传（最新为准）
     */
    void ensure(String sessionId, List<SandboxSeedFile> seeds);

    /**
     * 执行 shell 命令
     *
     * @param sessionId 会话标识
     * @param command   shell 命令行（实现侧负责在沙箱内执行，不逃逸到宿主）
     * @param timeoutMs 超时毫秒数（超时断开等待并置 {@link SandboxExecResult#timedOut()}；
     *                  进程终止语义由实现自定，见实现侧 javadoc——本端口不承诺 kill）
     * @return 执行结果（exitCode＋合并 stdout/stderr 截断后的 output＋timedOut）
     */
    SandboxExecResult exec(String sessionId, String command, long timeoutMs);

    /**
     * 读回沙箱内相对路径文件字节
     *
     * @param sessionId 会话标识
     * @param relPath   沙箱内相对路径（须相对；路径穿越校验由实现侧负责，非法路径抛
     *                  {@link IllegalArgumentException}）
     * @return 文件字节；文件不存在同样抛 {@link IllegalArgumentException}（固定人话
     *          「文件不存在于沙箱」，工具层统一转文本，不回 null）
     */
    byte[] fetch(String sessionId, String relPath);

    /**
     * 写入沙箱内相对路径文件（原件通道：把会话附件的<b>原始字节</b>放进沙箱，不做任何解析）。
     * <p>
     * 实现侧先 {@link #ensure}（会话沙箱不存在则懒建），再写文件；<b>目标路径被占用时在同一目录
     * 自动改名</b>（{@code <base>-2.<ext>}、{@code <base>-3.<ext>}…），不因同名抛异常——调用方
     * 无需探测可用路径。改名探测上限 100 次，超限抛 {@link IllegalArgumentException}（固定人话）。
     *
     * @param sessionId 会话标识
     * @param relPath   沙箱内相对路径（须相对；路径穿越校验由实现侧负责，非法路径抛
     *                  {@link IllegalArgumentException}）
     * @param bytes     文件原始字节（空数组合法：写入 0 字节文件；工具层可自定拒空）
     * @return <b>实际落点</b>（与 {@code relPath} 相同，或同目录自动改名后的相对路径）
     */
    String put(String sessionId, String relPath, byte[] bytes);

    /**
     * 沙箱<b>已存在</b>时写入相对路径文件（best-effort 通道：上传端点"沙箱已存在则上传即写入"）。
     * <p>
     * 契约与 {@link #put} 的差异只有两点：沙箱不存在 ⇒ <b>不创建</b>、返回 {@code false}；
     * 目标路径<b>已存在 ⇒ 同名覆盖为最新字节</b>（沙箱＝当前素材工作副本，{@code temp.txt} 这类
     * 重复名永远最新），返回 {@code true}。路径非法折算 {@code false}；底座不可达等中间层异常
     * 可上抛，由调用端（上传端点）兜底——不把"不可达"伪装成"未就绪"。
     *
     * @param sessionId 会话标识
     * @param relPath   沙箱内相对路径（须相对；非法路径折算 {@code false}）
     * @param bytes     文件原始字节（空数组合法：写入 0 字节文件）
     * @return 实际写入（含同名覆盖）返回 {@code true}；沙箱不存在/路径非法/未写入返回 {@code false}
     */
    boolean putIfAlive(String sessionId, String relPath, byte[] bytes);

    /**
     * 销毁沙箱会话（会话收口时调；幂等：已销毁/不存在时静默）
     *
     * @param sessionId 会话标识
     */
    void destroy(String sessionId);
}
