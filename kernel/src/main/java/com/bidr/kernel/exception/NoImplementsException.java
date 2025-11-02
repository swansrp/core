package com.bidr.kernel.exception;

import com.bidr.kernel.utils.JsonUtil;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Title: NoImplementsException
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/11/1 20:54
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class NoImplementsException extends RuntimeException {

    private Object obj;

    public NoImplementsException(String msg) {
        super(msg);
        log.debug(msg);
    }

    public NoImplementsException(Object obj, String msg) {
        super(msg);
        this.obj = obj;
        log.debug(msg, JsonUtil.toJson(obj));
    }
}