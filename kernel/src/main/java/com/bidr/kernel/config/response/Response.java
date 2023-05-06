package com.bidr.kernel.config.response;

import com.bidr.kernel.constant.err.ErrCode;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: Response
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/1/2 21:26
 */
@Data
@NoArgsConstructor
public class Response<T> {
    @ApiModelProperty("返回状态")
    private ApiResultStatus status;
    @ApiModelProperty("返回体")
    private T payload;

    @SuppressWarnings("unchecked")
    public Response(ServiceException exception) {
        ErrCode errCode = exception.getErrCode();
        this.status = new ApiResultStatus(errCode.getErrCode(), errCode.name(), exception.getMessage());
        this.payload = (T) new ExceptionResponse(errCode.getErrLevel(), errCode.getErrType(), exception.getMessage());
    }

    public Response(T data) {
        this.status = new ApiResultStatus(ErrCodeSys.SUCCESS.getErrCode(), ErrCodeSys.SUCCESS.name(), null);
        this.payload = data;
    }

    public Response(T data, String details) {
        this.status = new ApiResultStatus(ErrCodeSys.SUCCESS.getErrCode(), ErrCodeSys.SUCCESS.name(), details);
        this.payload = data;
    }
}
