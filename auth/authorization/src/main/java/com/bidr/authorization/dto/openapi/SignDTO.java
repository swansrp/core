package com.bidr.authorization.dto.openapi;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Title: SignDTO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 12:39
 */
@Data
public class SignDTO {
    @NotNull(message = "timeStamp 不能为空")
    private Long timeStamp;
    @NotNull(message = "nonce 不能为空")
    private String nonce;
    @NotNull(message = "signature 不能为空")
    private String signature;
}
