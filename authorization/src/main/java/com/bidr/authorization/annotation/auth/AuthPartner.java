package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.bo.account.UserInfo;
import com.bidr.authorization.bo.permit.PermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

import static com.bidr.kernel.constant.err.ErrCodeSys.SYS_SESSION_TIME_OUT;

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
    private final TokenService tokenService;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        String header = request.getHeader(RequestConst.AUTHORIZATION);
        String[] info = Base64Util.decode(header).split(":");
        AcPartner acPartner = acPartnerService.getByAppKey(info[0]);
        Validator.assertEquals(acPartner.getAppSecret(), info[1], SYS_SESSION_TIME_OUT);
        TokenInfo tokenInfo = tokenService.buildOpenPlatformToken(acPartner.getAppKey());
        tokenService.putItem(tokenInfo, TokenItem.PLATFORM.name(), acPartner.getPlatform());
        buildContext(tokenInfo);
    }

    private void buildContext(TokenInfo token) {
        TokenHolder.set(token);
        Validator.assertTrue(tokenService.isLoginToken(), SYS_SESSION_TIME_OUT);
        Map<String, Object> map = tokenService.getTokenValue(token);
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setExtraData(map);
        AccountContext.set(accountInfo);
    }
}