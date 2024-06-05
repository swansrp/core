package com.bidr.kernel.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: NoticeException
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/13 13:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeException extends RuntimeException {

    private Object obj;

    public NoticeException(String msg) {
        super(msg);
    }

    public NoticeException(Object obj, String msg) {
        super(msg);
        this.obj = obj;
    }
}
