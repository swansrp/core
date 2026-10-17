package com.bidr.platform.exception;

import com.bidr.kernel.exception.ServiceException;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;


/**
 * Title: RestTemplateException
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/28 18:30
 */
@Data
@NoArgsConstructor
public class RestTemplateException extends ServiceException {
    private HttpStatus status;
    private String msg;

    public RestTemplateException(HttpStatus status, String msg) {
        super(status + msg);
        this.status = status;
        this.msg = msg;
    }
}
