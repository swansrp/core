package com.bidr.authorization.dto.openapi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: OpenApiTokenRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:54
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OpenApiTokenRes {
    private String token;
    private int expired;
}
