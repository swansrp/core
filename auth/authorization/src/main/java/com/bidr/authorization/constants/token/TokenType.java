/**
 * Title: TokenType.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019-7-27 21:10
 * @description Project Name: Grote
 * @Package: com.srct.service.account.constants
 */
package com.bidr.authorization.constants.token;

import cn.hutool.core.util.EnumUtil;
import com.bidr.authorization.constants.common.ClientType;
import com.bidr.kernel.utils.FuncUtil;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum TokenType {
    /**
     * 各种类型的token
     */
    GUEST_TOKEN("9999"),
    // 网页接入token
    WEB_ACCESS_TOKEN("0"),
    // 网页刷新token
    WEB_REFRESH_TOKEN("2"),
    // 应用接入token
    APP_ACCESS_TOKEN("3"),
    // 应用刷新token
    APP_REFRESH_TOKEN("4"),
    // 开放平台Token
    PLATFORM_TOKEN("5"),
    // 微信接入token
    WECHAT_TOKEN("6"),
    // 修改密码token
    CHANGE_PWD_TOKEN("7"),
    // iot token
    IOT_TOKEN("10");

    private final String value;

    public static TokenType accessTokenType(String clientType) {
        ClientType clientTypeConst = EnumUtil.getBy(ClientType::getValue, clientType);
        if (FuncUtil.equals(clientTypeConst, ClientType.WEB)) {
            return WEB_ACCESS_TOKEN;
        } else if (FuncUtil.equals(clientTypeConst, ClientType.APP)) {
            return APP_ACCESS_TOKEN;
        } else if (FuncUtil.equals(clientTypeConst, ClientType.WECHAT)) {
            return WECHAT_TOKEN;
        } else if (FuncUtil.equals(clientTypeConst, ClientType.PLATFORM)) {
            return PLATFORM_TOKEN;
        }
        return GUEST_TOKEN;
    }

    public static TokenType refreshTokenType(String clientType) {
        ClientType clientTypeConst = EnumUtil.getBy(ClientType::getValue, clientType);
        if (FuncUtil.equals(clientTypeConst, ClientType.WEB)) {
            return WEB_REFRESH_TOKEN;
        } else if (FuncUtil.equals(clientTypeConst, ClientType.APP)) {
            return APP_REFRESH_TOKEN;
        }
        return null;
    }

    public static TokenType of(String value) {
        return EnumUtil.getBy(TokenType::getValue, value);
    }
}
