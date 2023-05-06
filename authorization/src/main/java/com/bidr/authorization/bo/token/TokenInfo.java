package com.bidr.authorization.bo.token;

import com.bidr.authorization.constants.token.TokenType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: TokenInfo
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/28 14:33
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TokenInfo {
    private String token;
    private TokenType type;
    private String customerNumber;
}
