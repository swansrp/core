package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Title: AuthPartner
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/9/10 15:03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthPartner implements AuthRole {
    private final AcPartnerService acPartnerService;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        String header = request.getHeader(RequestConst.X_API_KEY);
        String[] info = Base64Util.decode(header).split(":");
        AcPartner acPartner = acPartnerService.getByAppKey(info[0]);
        Validator.assertNotNull(acPartner, AccountErrCode.AC_PARTNER_NOT_EXISTED);
        Validator.assertEquals(acPartner.getAppSecret(), info[1], AccountErrCode.AC_PARTNER_SECRET_INVALID);
        buildContext(acPartner);
    }

    private void buildContext(AcPartner acPartner) {
        Map<String, Object> map = new HashMap<>(1);
        map.put(TokenItem.PLATFORM.name(), acPartner.getPlatform());
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setExtraData(map);
        AccountContext.set(accountInfo);
    }
}