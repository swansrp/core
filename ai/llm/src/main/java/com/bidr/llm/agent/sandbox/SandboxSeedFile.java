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

    /**
     * 紧凑构造器防御性拷贝（review #12）：{@code byte[]} 可变，调用方传入后改动原数组
     * 不得影响本记录（种子清单是单例 Bean 的跨会话共享态）；null 容错归一为空数组
     * （「非 null」契约的兜底，不因调用方疏漏炸构造）。
     */
    public SandboxSeedFile {
        content = content == null ? new byte[0] : content.clone();
    }

    /** 取出侧同样克隆：拿到字节的调用方改数组不回写进记录（与紧凑构造器成对） */
    @Override
    public byte[] content() {
        return content.clone();
    }
}
