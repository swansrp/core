package com.bidr.authorization.service.login;

import com.bidr.authorization.dto.openapi.OpenApiTokenRcv;
import com.bidr.authorization.dto.openapi.OpenApiTokenRes;
import com.bidr.authorization.dto.openapi.SignDTO;

/**
 * Title: OpenApiLoginService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:20
 */
public interface OpenApiLoginService {
    /**
     * 获取对接token
     *
     * @param sign 参数
     * @return
     */
    OpenApiTokenRes getToken(OpenApiTokenRcv sign);

    /**
     * 生成签名测试接口
     *
     * @param appKey
     * @return
     */
    SignDTO sign(String appKey);
}
