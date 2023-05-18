package com.bidr.authorization.vo.token;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

/**
 * Title: TokenReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/17 14:43
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenReq {
    @NotNull(message = "token不能未空")
    private String token;
}
