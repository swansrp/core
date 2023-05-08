/**
 * Title: RequestConst.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019-7-28 8:03
 */
package com.bidr.authorization.constants.common;

import com.bidr.kernel.utils.StringUtil;

public class RequestConst {
    public static final String TOKEN = "Authorization";
    public static final String ID_TOKEN = "id_token";
    public static final String API_VERSION = "api-version";
    public static final String OPERATOR = "operator";
    public static final String AUTHORIZATION = "Authorization";
    public static final String CLIENT_TYPE = "client-type";
    public static final String X_REQUESTED = "X-Requested-With";

    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String PUT = "PUT";
    public static final String DELETE = "DELETE";
    public static final String OPTIONS = "OPTIONS";


    public static final String APP_KEY = "appKey";
    public static final String APP_SECRET = "appSecret";

    public static final String CONTENT_TYPE = "content-type";

    public static final String SERVICE_API = "serviceApi";
    public static final String OBJECT = "object";

    public static String getAllHeader() {
        return StringUtil.joinWith(",", TOKEN, ID_TOKEN, API_VERSION, OPERATOR, AUTHORIZATION, CLIENT_TYPE, CONTENT_TYPE,
                X_REQUESTED);
    }

    public static String getAllMethod() {
        return StringUtil.joinWith(",", GET, PUT, POST, OPTIONS, DELETE);
    }
}
