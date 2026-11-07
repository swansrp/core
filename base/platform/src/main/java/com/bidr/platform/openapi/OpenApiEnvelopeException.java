package com.bidr.platform.openapi;

/**
 * Title: OpenApiEnvelopeException
 * Description: 开放口<b>非成功信封</b>异常（方案B 棒4 §2「core 传输」层）——
 * {@link OpenApiDataClient} 解包遇 {@code status.code!=0} 抛出，携 code/msg。
 *
 * <p>🔴 必须 {@code extends IllegalStateException}：保 {@code IdentitySnapshotClient} 既有契约
 * 「非零信封抛 IllegalStateException 带 status.msg」（其线格式契约测试按
 * {@code IllegalStateException} 断言且断言不可改）——信封解包下沉 platform 后，异常类型链
 * 不能变。message 同时含 code 与 msg（消费侧日志一条能看到「错在哪＋为什么」）。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class OpenApiEnvelopeException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /** 信封错误码（status.code；信封缺 code/status 时为 null） */
    private final Integer code;

    /**
     * @param code 信封错误码（可 null——信封缺 status/code 的残缺形态）
     * @param msg  信封 status.msg（可 null——线上未带文案）
     */
    public OpenApiEnvelopeException(Integer code, String msg) {
        super("开放口信封非成功码: " + code + (msg == null || msg.isEmpty() ? "" : " - " + msg));
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
