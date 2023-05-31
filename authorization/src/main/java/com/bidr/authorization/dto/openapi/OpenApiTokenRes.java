package com.bidr.authorization.dto.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Title: OpenApiTokenRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:54
 */
@Data
@AllArgsConstructor
public class OpenApiTokenRes {

    private String token;
    private long expired;
}
