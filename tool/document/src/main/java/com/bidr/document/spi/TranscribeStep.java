package com.bidr.document.spi;

import java.io.IOException;

/**
 * Title: TranscribeStep
 * Description: 文件加工流水线的第 3 槽——<b>可选的外部增强转写腿</b>（如 tool/mineru）。
 * 契约自 {@code com.bidr.llm.parse.DocumentTranscriber} 原样带过来：{@link #isAvailable(String)}
 * 认领的文件先走外部转写，转写抛异常或返回空时<b>门面回落内置腿</b>（文本直读/POI/PDFBox/多模态）——
 * 门面负责回落，实现方<b>别吞错</b>，失败如实抛。
 * <p>
 * 契约要点：
 * <ul>
 *   <li>装配就绪度不入装配条件、留给 {@code isAvailable} 运行期判定（配置全空＝不认领＝零变化），
 *       避免「Bean 在但不生效」的歧义；</li>
 *   <li>{@code isAvailable} 只按「本腿能不能处理这个文件」回答（类型＋配置就绪），
 *       不承诺一定成功——成功与否由 {@code transcribe} 的返回/异常说话；</li>
 *   <li>实现应自带等待预算（同步阻塞时长上限）并在超时时抛异常，把「等多久」的控制权留给配置；</li>
 *   <li>选型：yml 一把键 {@code tool.document.transcriber}（纯 Spring Environment，零 sys_config），
 *       实现模块的 Bean 应以 {@code @ConditionalOnProperty(havingValue = "<腿名>")} 自 gate——
 *       同一时刻至多一条增强腿在装（R-3）；配了但包没引＝WARN「选型指向不存在的腿，已走内置」
 *       继续跑（三态之二）。</li>
 * </ul>
 * 依赖方向：实现模块（tool/mineru 等）→ 本口；本模块不反向依赖任何实现（腿→口，无环）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public interface TranscribeStep {

    /**
     * 腿名＝yml 选型键 {@code tool.document.transcriber} 的取值对它点名（如 {@code mineru}）。
     */
    String legName();

    /**
     * 本腿是否认领该文件。
     *
     * @param filename 原始文件名（含扩展名；按扩展名与自身配置就绪度判定）
     */
    boolean isAvailable(String filename);

    /**
     * 外部转写为 Markdown（同步阻塞，受实现自身等待预算约束）。
     *
     * @param content  文件字节
     * @param filename 原始文件名
     * @return markdown 正文（非空）
     * @throws IOException 转写失败／超时／未就绪——抛出后门面回落内置腿
     */
    String transcribe(byte[] content, String filename) throws IOException;
}
