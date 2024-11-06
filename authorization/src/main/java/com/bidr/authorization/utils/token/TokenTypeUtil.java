/**
 * Title: TokenTypeUtil.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-8-3 16:00
 * @description Project Name: Grote
 * @Package: com.srct.service.account.utils
 */
package com.bidr.authorization.utils.token;


import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;

public class TokenTypeUtil {

    public static String getTokenTypeClientType(String clientType) {
        Validator.assertNotBlank(clientType, ErrCodeSys.PA_DATA_NOT_EXIST, "客户端类型");
        switch (ClientType.valueOf(clientType)) {
            case WEB:
                return TokenType.WEB_ACCESS_TOKEN.name();
            case APP:
                return TokenType.APP_ACCESS_TOKEN.name();
            case WECHAT:
                return TokenType.WECHAT_TOKEN.name();
            case IOT:
                return TokenType.IOT_TOKEN.name();
            case PLATFORM:
                return TokenType.PLATFORM_TOKEN.name();
            default:
                Validator.assertNotBlank(clientType, ErrCodeSys.SYS_ERR_MSG, "未知的客户端类型");
                return null;
        }
    }
}
