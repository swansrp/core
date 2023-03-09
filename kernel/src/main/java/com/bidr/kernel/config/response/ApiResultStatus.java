package com.bidr.kernel.config.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Title: ApiResultStatus
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/09 13:10
 */
@Data
@AllArgsConstructor
public class ApiResultStatus {
    private Integer code;
    private String msg;
    private String details;
}
