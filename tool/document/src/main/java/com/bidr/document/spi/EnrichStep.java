package com.bidr.document.spi;

/**
 * Title: EnrichStep
 * Description: 文件加工流水线的第 4 槽——<b>Markdown 后处理增强</b>（正文已成、产物未落形）：
 * 如补标题层级、清理解压噪声、统一表格样式。多个实现按装配序串行，前一步输出＝后一步输入。
 * <p>
 * 内置实现<b>缺席</b>＝直通不改写（缺省行为与历史逐字一致）。本槽是纯函数式的正文改写，
 * 不许在这里抛「加工失败」——发现正文不可用应在上游抛，这里只做可降级的修饰。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public interface EnrichStep {

    /**
     * 正文增强（返回改写后的 markdown；返回 null＝保留原文，防御实现失误）。
     *
     * @param markdown 已转出的 markdown 正文
     * @param filename 原始文件名（convert 腿在场时为转换后名）
     */
    String enrich(String markdown, String filename);
}
