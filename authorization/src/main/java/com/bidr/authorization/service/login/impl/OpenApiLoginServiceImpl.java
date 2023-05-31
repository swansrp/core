package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dto.openapi.OpenApiTokenRcv;
import com.bidr.authorization.dto.openapi.OpenApiTokenRes;
import com.bidr.authorization.dto.openapi.SignDTO;
import com.bidr.authorization.openapi.utils.OpenApiUtil;
import com.bidr.authorization.service.login.OpenApiLoginService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Title: OpenApiLoginServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:37
 */
@Service
@RequiredArgsConstructor
public class OpenApiLoginServiceImpl implements OpenApiLoginService {

    private final AcPartnerService acPartnerService;
    private final TokenService tokenService;

    @Override
    public OpenApiTokenRes getToken(OpenApiTokenRcv sign) {
        AcPartner acPartner = acPartnerService.getByAppKey(sign.getAppKey());
        Validator.assertNotNull(acPartner, AccountErrCode.AC_USER_NOT_EXISTED);
        OpenApiUtil.validateSign(sign.getTimeStamp(), sign.getNonce(), sign.getSignature(), acPartner.getAppSecret());
        return tokenService.buildOpenPlatformToken(acPartner.getAppKey());
    }

    @Override
    public SignDTO sign(String appKey) {
        AcPartner acPartner = acPartnerService.getByAppKey(appKey);
        Validator.assertNotNull(acPartner, AccountErrCode.AC_USER_NOT_EXISTED);
        return OpenApiUtil.sign(acPartner.getAppSecret());

    }
}
