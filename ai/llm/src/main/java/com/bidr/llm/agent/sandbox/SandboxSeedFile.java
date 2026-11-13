package com.bidr.llm.agent.sandbox;

/**
 * Title: SandboxSeedFile
 * Description: 沙箱种子文件（不可变记录）——{@code AgentSandbox#ensure} 预置进沙箱的
 * 最小单元：agent 定义经 {@code AbstractToolLoopAgent#sandboxSeedFiles} 钩子给出
 * （如技能脚本/数据样例），首次建沙箱时一次性写入；棒3b 的 epc 侧是首个消费方。
 *
 * @param relPath 沙箱内相对路径（含文件名，如 {@code scripts/check.py}；穿越校验由实现侧负责）
 * @param content 文件字节（非 null；空文件传空数组）
 * @author Sharp
 * @since 2026/11/14
 */
public record SandboxSeedFile(String relPath, byte[] content) {
}
