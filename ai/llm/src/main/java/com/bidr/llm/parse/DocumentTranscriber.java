package com.bidr.llm.parse;

import java.io.IOException;

/**
 * Title: DocumentTranscriber
 * Description: 可选的外部文档转录腿（SPI）。实现模块（如 core/tool/mineru）引在 classpath 上、
 * 注册了本口 Bean，{@link FileMarkdownService} 才会在格式分发前<b>优先</b>咨询它：
 * {@link #isAvailable(String)} 认领的文件先走外部转写，转写抛异常或返回空时回落既有本地路径
 * （文本直读/POI/PDFBox/多模态转录）——门面负责回落，实现方<b>别吞错</b>，失败如实抛。
 * <p>
 * 契约要点：
 * <ul>
 *   <li>本口 Bean 缺席（没引实现包）＝门面行为与历史完全一致，零配置零变化；</li>
 *   <li>{@code isAvailable} 只按「本腿能不能处理这个文件」回答（类型＋配置就绪），
 *       不承诺一定成功——成功与否由 {@code transcribe} 的返回/异常说话；</li>
 *   <li>实现应自带等待预算（同步阻塞时长上限）并在超时时抛异常，把「等多久」的控制权留给配置，
 *       不做任务句柄化（要异步的调用方直接用实现模块的低层客户端）。</li>
 * </ul>
 * 依赖方向：实现模块 → llm（本口在 llm）；llm 不反向依赖任何实现。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public interface DocumentTranscriber {

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
     * @throws IOException 转写失败／超时／未就绪——抛出后门面回落既有本地路径
     */
    String transcribe(byte[] content, String filename) throws IOException;
}
