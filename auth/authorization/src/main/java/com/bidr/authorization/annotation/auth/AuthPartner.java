package com.bidr.authorization.annotation.auth;

import com.bidr.authorization.bo.account.AccountInfo;
import com.bidr.authorization.config.log.LogFilter;
import com.bidr.authorization.config.log.MultiReadHttpServletResponse;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.entity.AcPartnerHistory;
import com.bidr.authorization.dao.repository.AcPartnerHistoryService;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.kernel.config.response.Response;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.kernel.utils.HttpUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Date;
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
    private final AcPartnerHistoryService acPartnerHistoryService;

    @Override
    public void validate(HttpServletRequest request, String... args) {
        String header = request.getHeader(RequestConst.X_API_KEY);
        Validator.assertNotBlank(header, AccountErrCode.AC_PARTNER_NOT_EXISTED);
        String[] info = Base64Util.decode(header).split(":");
        AcPartner acPartner = acPartnerService.getByAppKey(info[0]);
        Validator.assertNotNull(acPartner, AccountErrCode.AC_PARTNER_NOT_EXISTED);
        Validator.assertEquals(acPartner.getAppSecret(), info[1], AccountErrCode.AC_PARTNER_SECRET_INVALID);
        buildContext(acPartner);
        request.setAttribute(AcPartnerHistory.class.getName(), buildPartnerHistory(request, acPartner));
    }

    private AcPartnerHistory buildPartnerHistory(HttpServletRequest request, AcPartner acPartner) {
        AcPartnerHistory history = new AcPartnerHistory();
        history.setPlatform(acPartner.getPlatform());
        history.setAppKey(acPartner.getAppKey());
        history.setRemoteIp(HttpUtil.getRemoteIp(request));
        history.setUrl(request.getRequestURI());
        history.setRequestAt(new Date());
        return history;
    }

    private void buildContext(AcPartner acPartner) {
        Map<String, Object> map = new HashMap<>(1);
        map.put(TokenItem.PLATFORM.name(), acPartner.getPlatform());
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setExtraData(map);
        AccountContext.set(accountInfo);
    }

    @Override
    public void completion(HttpServletRequest request, HttpServletResponse response) {
        AcPartnerHistory history = (AcPartnerHistory) request.getAttribute(AcPartnerHistory.class.getName());
        history.setStatus(response.getStatus());
        history.setResponseAt(new Date());
        if (LogFilter.isJsonResponse(response) && response instanceof MultiReadHttpServletResponse) {
            String message = LogFilter.extractResultPayload((MultiReadHttpServletResponse) response);
            Response<?> resp = JsonUtil.readJson(message, Response.class, Object.class);
            history.setMessage(JsonUtil.toJson(resp.getStatus(), false, false, true));
        }
        acPartnerHistoryService.insert(history);
    }
}