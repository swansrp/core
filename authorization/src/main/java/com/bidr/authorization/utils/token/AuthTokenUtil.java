package com.bidr.authorization.utils.token;


import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.token.TokenType;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import org.springframework.validation.annotation.Validated;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: AuthTokenUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019/10/9 15:07
 */
public class AuthTokenUtil {

    private static final String BASE_KEY = "Token";
    private static final String SEPARATION = ":";

    public static TokenInfo extractToken(HttpServletRequest request) {
        return resolveToken(request.getHeader(RequestConst.TOKEN));
    }

    public static TokenInfo resolveToken(String token) {
        if (FuncUtil.isNotEmpty(token)) {
            Validator.assertTrue(token.startsWith("Bearer "), ErrCodeSys.SYS_SESSION_TIME_OUT);
            String[] array = token.split(" ");
            if (array.length > 1) {
                return decode(array[array.length - 1]);
            }
        }
        return null;
    }

    public static TokenInfo decode(String token) {
        String str = Base64Util.decode(token);
        List<String> list = StringUtil.split(str, SEPARATION);
        return new TokenInfo(list.get(0), TokenType.of(list.get(1)), list.get(2));
    }

    public static TokenInfo buildToken(String token, TokenType tokenType, String customerNumber) {
        return new TokenInfo(token, tokenType, customerNumber);
    }

    public static String getKeyFromToken(String token) {
        TokenInfo info = decode(token);
        return getKey(info);
    }

    public static String getKey(TokenInfo tokenInfo) {
        return getPrefix(tokenInfo.getType(), tokenInfo.getCustomerNumber()) + SEPARATION + tokenInfo.getToken();
    }

    public static String getPrefix(TokenType tokenType, String customerNumber) {
        return BASE_KEY + SEPARATION + customerNumber + SEPARATION + tokenType.name();
    }

    public static String getKey(String token, TokenType tokenType, String customerNumber) {
        return getPrefix(tokenType, customerNumber) + token;
    }

    public static String getTokenFromKey(String key) {
        String[] keyArray = key.split(SEPARATION);
        return getToken(keyArray[2], TokenType.of(keyArray[1]), keyArray[0]);
    }

    public static String getToken(String token, TokenType tokenType, String customerNumber) {
        TokenInfo tokenInfo = new TokenInfo(token, tokenType, customerNumber);
        return getToken(tokenInfo);
    }

    public static String getToken(TokenInfo tokenInfo) {
        return encode(tokenInfo);
    }

    private static String encode(TokenInfo tokenInfo) {
        String tokenStr = StringUtil.joinWith(SEPARATION, tokenInfo.getToken(), tokenInfo.getType().getValue(),
                tokenInfo.getCustomerNumber());
        return Base64Util.encode(tokenStr);
    }

    public static TokenType getTokenType(String token) {
        TokenInfo tokenInfo = decode(token);
        return tokenInfo.getType();
    }

    public static Map<String, String> decodeAuthorization(HttpServletRequest request) {
        Map<String, String> authMap = new HashMap<>(2);
        try {
            Map<String, String> headersInfoMap = HttpUtil.getHeadersInfoMap(request);
            String authorization = headersInfoMap.get(RequestConst.AUTHORIZATION);
            String keyPair = Base64Util.decode(authorization.split(" ")[1]);
            String[] strArray = keyPair.split(":");
            authMap.put(RequestConst.APP_KEY, strArray[0]);
            authMap.put(RequestConst.APP_SECRET, strArray[1]);
        } catch (Exception e) {

        }
        return authMap;
    }
}
