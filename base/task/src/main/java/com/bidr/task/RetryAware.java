package com.bidr.task;

/**
 * 异常的可重试语义口（M16 棒 2）：照现网 {@code OfficeConvertException} 的布尔形状，
 * 不逼业务重造异常族。
 *
 * <ul>
 *   <li>{@code retryable=false}：转换层面/数据层面的失败——重跑结果不变，切面直接置 DEAD；</li>
 *   <li>{@code retryable=true}：临时故障——服务不可达、5xx；切面置 FAILED＋退避，交给重试预算；
 *       🔴 静默兜底会把「暂时没页」固化成「永远没页」，故默认（未实现本口）＝可重试。</li>
 * </ul>
 *
 * <p>另有按类型的补口：{@link AsyncStage#noRetryOn()} 指名即不重试（不用改异常族）。</p>
 *
 * @author sharp
 */
public interface RetryAware {

    /** 是否值得重试：false=直接 DEAD（重跑结果不变），true=FAILED＋退避走预算（临时故障） */
    boolean isRetryable();
}
