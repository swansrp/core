package com.bidr.document.spi;

/**
 * Title: ConvertStep
 * Description: 文件加工流水线的第 2 槽——<b>office→PDF 外挂转换腿</b>（如 tool/gotenberg）。
 * <p>
 * 🔴 convert 槽<b>没有内置实现</b>（D10 定形）：内置 POI 路线绕过本槽直接吃 office 原件字节，
 * 出得了正文、出不了 PDF/页码/页图/pagemap——所以「腿缺席＝内置兜底」与「两条路线质量分档」
 * 是本槽的天生语义，产物差异靠 {@code RouteTrace} 的降级标记说出去，不许伪装成同一档。
 * <p>
 * 🔴 失败语义两半<b>不许合并</b>（{@link DocumentConvertException#isRetryable()}）：
 * <ul>
 *   <li>转不动（{@code retryable=false}：文档本身 4xx、引擎未配置、产出不是 PDF）＝稳定状态，
 *       门面兜底按原件往下送并记 CONVERT_FALLBACK 快照；</li>
 *   <li>不可达/5xx（{@code retryable=true}）＝临时故障，门面<b>原样上抛</b>交调用方的重试预算——
 *       静默兜底会把「暂时没页」固化成「永远没页」。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/10/20
 */
public interface ConvertStep {

    /**
     * 本腿是否认领该文件（按扩展名与自身配置就绪度判定；只答「能不能」，不承诺一定成功）。
     */
    boolean isConvertible(String filename);

    /**
     * office 文档转成 PDF。
     *
     * @param fileName 原始文件名（实现一般只用扩展名判断输入格式）
     * @param content  原件字节
     * @return PDF 字节（实现须按魔数自证）
     * @throws DocumentConvertException retryable=false＝稳定失败可兜底；true＝临时故障须上抛重试
     */
    byte[] convertToPdf(String fileName, byte[] content);

    /**
     * 腿名（路线留痕与日志用；同槽多腿时按装配序取第一条认领者）。
     */
    default String legName() {
        return getClass().getSimpleName();
    }
}
