package com.bidr.authorization.dto;

import lombok.Data;

/**
 * Title: SignDTO
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 12:39
 */
@Data
public class SignDTO {
    private Long timeStamp;
    private String nonce;
    private String signature;
}
