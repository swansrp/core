package com.bidr.authorization.dto.openapi;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

/**
 * Title: OpenApiTokenRcv
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:56
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenApiTokenRcv extends SignDTO {
    @NotNull(message = "appKey 不能为空")
    private String appKey;
}
