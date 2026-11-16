package com.bidr.llm.agent.sandbox;

/**
 * Title: SandboxExecResult
 * Description: 沙箱命令执行结果（不可变记录）——{@code AgentSandbox#exec} 的返回单元，
 * {@code sandbox_run} 工具据此组装回给模型的文本。
 *
 * @param exitCode 进程退出码（正常结束＝进程码；超时/未取得码时由实现侧给出约定值）
 * @param output   合并 stdout/stderr 后的输出文本（实现侧截断到有界长度；可空）
 * @param timedOut 是否超时（true＝命令未在时限内跑完；进程终止语义由实现自定，见
 *                 {@code AgentSandbox#exec}——端口不承诺 kill；output 可能为空，不保证留有部分输出）
 * @author Sharp
 * @since 2026/11/14
 */
public record SandboxExecResult(int exitCode, String output, boolean timedOut) {
}
