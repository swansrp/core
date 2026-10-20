package com.bidr.document;

/**
 * Title: DocumentConvertException
 * Description: convert 腿（office→PDF）失败，带<b>可重试语义两半</b>（形状自 InkHub
 * {@code OfficeConvertException} 下沉，语义一字不动）：
 * <ul>
 *   <li>{@code retryable=false}：转换层面的失败——文档本身转不动（引擎 4xx）、引擎未装配/未配置、
 *       产出不是 PDF。重跑结果不变，门面应<b>兜底</b>（原件直送、记 CONVERT_FALLBACK、引用降级无页）；</li>
 *   <li>{@code retryable=true}：临时故障——服务不可达、5xx。静默兜底会把「暂时没页」变成
 *       「永远没页」，门面应<b>原样上抛</b>，交给调用方的重试预算。</li>
 * </ul>
 * 🔴 两半不许合并、不许在实现里静默吞掉——分诊是调用方的事。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class DocumentConvertException extends RuntimeException {

    /** 是否值得重试：false＝兜底（转换层面的失败），true＝上抛走重试（临时故障） */
    private final boolean retryable;

    public DocumentConvertException(boolean retryable, String message) {
        super(message);
        this.retryable = retryable;
    }

    public DocumentConvertException(boolean retryable, String message, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
