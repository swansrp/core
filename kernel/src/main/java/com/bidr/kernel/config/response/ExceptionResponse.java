package com.bidr.kernel.config.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Title: ExceptionResponse
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/6 17:00
 */

@Data
@AllArgsConstructor
public class ExceptionResponse {
    private String errLevel;
    private String errType;
    private String errMsg;
}
