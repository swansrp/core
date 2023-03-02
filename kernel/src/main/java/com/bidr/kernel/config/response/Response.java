package com.bidr.kernel.config.response;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: Response
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/1/2 21:26
 */
@Data
public class Response<T> {
    @ApiModelProperty("返回编码")
    private String code;
    @ApiModelProperty("返回消息")
    private String message;
    @ApiModelProperty("返回体")
    private T data;

    @SuppressWarnings("unchecked")
    public Response(ServiceException exception) {
        ErrCode errCode = exception.getErrCode();
        this.code = errCode.getErrCode();
        this.message = errCode.name();
        this.data = (T) new ExceptionResponse(errCode.getErrLevel(), errCode.getErrType(), exception.getMessage());
    }

    public Response(T data) {
        this.code = ErrCodeSys.SUCCESS.getErrCode();
        this.message = ErrCodeSys.SUCCESS.name();
        this.data = data;
    }
}
