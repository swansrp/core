package com.bidr.parse;

import com.bidr.task.RetryAware;

/**
 * Title: ParseContractException
 * Description: 解析编排的契约违约（D-11 未知产物键／payload 缺必备坐标这类
 * "重跑结果不变"的永久失败）：实现 {@link RetryAware} 且 {@code retryable=false}——
 * 切面当场置 DEAD 不进重试预算（静默子集错位＝下游读空，最坏失败模式必须提交时炸）。
 *
 * @author Sharp
 * @since 2026-10-21
 */
public class ParseContractException extends RuntimeException implements RetryAware {

    private static final long serialVersionUID = 1L;

    public ParseContractException(String message) {
        super(message);
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}
