package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.dto.openapi.OpenApiTokenRcv;
import com.bidr.authorization.dto.openapi.OpenApiTokenRes;
import com.bidr.authorization.dto.openapi.SignDTO;
import com.bidr.authorization.openapi.utils.OpenApiUtil;
import com.bidr.authorization.service.login.OpenApiLoginService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
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
        Validator.assertNotNull(acPartner, AccountErrCode.AC_PARTNER_NOT_EXISTED);
        Validator.assertNotEquals(acPartner.getStatus(), ActiveStatusDict.ACTIVATE.getValue(), AccountErrCode.AC_PARTNER_NOT_AVAILABLE);
        OpenApiUtil.validateSign(sign.getTimeStamp(), sign.getNonce(), sign.getSignature(), acPartner.getAppSecret());
        TokenInfo tokenInfo = tokenService.buildOpenPlatformToken(acPartner.getAppKey());
        tokenService.putItem(tokenInfo, TokenItem.PLATFORM.name(), acPartner.getPlatform());
        return new OpenApiTokenRes(AuthTokenUtil.getToken(tokenInfo), tokenInfo.getExpired());
    }

    @Override
    public SignDTO sign(String appKey) {
        AcPartner acPartner = acPartnerService.getByAppKey(appKey);
        Validator.assertNotNull(acPartner, AccountErrCode.AC_USER_NOT_EXISTED);
        return OpenApiUtil.sign(acPartner.getAppSecret());

    }
}
