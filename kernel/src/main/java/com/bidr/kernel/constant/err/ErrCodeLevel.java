package com.bidr.kernel.constant.err;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;

/**
 * Title: ErrCodeLevel
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2022/6/6 11:27
 */
@Getter
@AllArgsConstructor
public enum ErrCodeLevel {
    /**
     * errCode等级
     */
    FATAL("5"),
    ERROR("4"),
    WARN("3"),
    INFO("2"),
    DEBUG("1"),
    TRACE("0"),
    HIDE("");

    private final String value;

    public static void log(Logger log, ErrCodeLevel errCodeLevel, Throwable throwable) {
        switch (errCodeLevel) {
            case HIDE:
                log.info(throwable.getMessage());
                break;
            case TRACE:
                log.trace("", throwable);
                break;
            case DEBUG:
                log.debug("", throwable);
                break;
            case INFO:
                log.info("", throwable);
                break;
            case WARN:
                log.warn("", throwable);
                break;
            case FATAL:
                log.error("严重错误", throwable);
                break;
            default:
                log.error("", throwable);
                break;
        }
    }
}
