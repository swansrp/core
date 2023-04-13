package com.bidr.kernel.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: NoticeException
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/13 13:55
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeException extends RuntimeException {
    public NoticeException(String msg) {
        super(msg);
    }
}
