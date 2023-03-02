package com.sharp.authorization.utils.token;


import com.sharp.authorization.constants.common.RequestConst;
import com.sharp.kernel.constant.err.ErrCodeSys;
import com.sharp.kernel.exception.ServiceException;
import com.sharp.kernel.utils.Base64Util;
import com.sharp.kernel.utils.BeanUtil;
import com.sharp.kernel.utils.HttpUtil;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: AuthTokenUtil
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019/10/9 15:07
 */
public class AuthTokenUtil {

    private static final String STRICT_TOKEN_FORMAT_ENV = "prod";

    public static String extractToken(HttpServletRequest request) {
        return resolveToken(request.getHeader(RequestConst.TOKEN));
    }

    public static String resolveToken(String token) {
        try {
            // 去掉令牌前缀
            return Base64Util.decode(token);
        } catch (Exception e) {
            if (StringUtils.equals(BeanUtil.getActiveProfile(), STRICT_TOKEN_FORMAT_ENV)) {
                throw new ServiceException(ErrCodeSys.PA_PARAM_FORMAT, "token");
            } else {
                return token;
            }
        }
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
