package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

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

    @Override
    public void validate(HttpServletRequest request, String... args) {
        String header = request.getHeader(RequestConst.AUTHORIZATION);
        String[] info = Base64Util.decode(header).split(":");
        AcPartner acPartner = acPartnerService.getByAppKey(info[0]);
        Validator.assertEquals(acPartner.getAppSecret(), info[1], SYS_SESSION_TIME_OUT);
    }
}