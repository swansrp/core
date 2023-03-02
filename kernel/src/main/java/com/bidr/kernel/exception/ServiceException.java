package com.bidr.kernel.exception;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Objects;

/**
 * Title: ServiceException
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 11:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ResponseStatus(HttpStatus.OK)
public class ServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private ErrCode errCode;

    private Object errObj;

    public ServiceException() {
        super();
    }

    public ServiceException(String msg, Exception e) {
        super(ErrCodeSys.SYS_ERR_MSG.getErrText(msg + "\n" + e.getMessage()));
        errCode = ErrCodeSys.SYS_ERR_MSG;
    }

    public ServiceException(String msg) {
        super(ErrCodeSys.SYS_ERR_MSG.getErrText(msg));
        errCode = ErrCodeSys.SYS_ERR_MSG;
    }

    public ServiceException(ErrCode err, Object... parameterArr) {
        super(err.getErrText(parameterArr));
        errCode = err;
    }

    public ServiceException(MethodArgumentNotValidException ex) {
        super(ErrCodeSys.SYS_VALIDATE_NOT_PASS.getErrText(Objects.requireNonNull(ex.getBindingResult().getFieldError()).getDefaultMessage()));
        errCode = ErrCodeSys.SYS_VALIDATE_NOT_PASS;
    }
}
