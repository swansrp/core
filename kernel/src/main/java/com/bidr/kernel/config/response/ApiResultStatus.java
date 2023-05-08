package com.bidr.kernel.config.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: ApiResultStatus
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 13:10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResultStatus {
    private Integer code;
    private String msg;
    private String details;
}
