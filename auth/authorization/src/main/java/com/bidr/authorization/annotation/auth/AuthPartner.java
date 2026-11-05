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
import com.bidr.authorization.holder.TenantContext;
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
        // split(":", 2)：秘钥里带冒号不至于截断失配；空秘钥（"appKey:"）不会越界成 500
        String[] info = Base64Util.decode(header).split(":", 2);
        Validator.assertTrue(info.length == 2, AccountErrCode.AC_PARTNER_SECRET_INVALID);
        AcPartner acPartner = acPartnerService.getByAppKey(info[0]);
        Validator.assertNotNull(acPartner, AccountErrCode.AC_PARTNER_NOT_EXISTED);
        Validator.assertEquals(acPartner.getAppSecret(), info[1], AccountErrCode.AC_PARTNER_SECRET_INVALID);
        buildContext(acPartner);
        // 机器凭证＝平台级服务间凭证（ac_partner 本就是平台登记面）：显式置平台档，
        // 否则 TenantDataGate 判序会把本上下文当「已登录无租户非平台」，ac_user 锚点查询
        // 被注入 1=0 —— open-api 成员同步按登录名回填（getById(ac_user)）就恒空
        // （6.60 真机首验抓到：projects/teams 有数、members 全空）。AuthAdmin 同款：只有
        // 校验全过才可达标记；TenantContext 由 AuthInterceptor.afterCompletion 每请求 remove。
        TenantContext.markPlatform();
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
        Map<String, Object> map = new HashMap<>(2);
        map.put(TokenItem.PLATFORM.name(), acPartner.getPlatform());
        // appKey 烤入上下文：IdP 账密换票端点取它作签发 claim 的 app_id（票的受众接入方）
        map.put(TokenItem.APP_KEY.name(), acPartner.getAppKey());
        AccountInfo accountInfo = new AccountInfo();
        accountInfo.setExtraData(map);
        AccountContext.set(accountInfo);
    }

    @Override
    public void completion(HttpServletRequest request, HttpServletResponse response) {
        AcPartnerHistory history = (AcPartnerHistory) request.getAttribute(AcPartnerHistory.class.getName());
        // 鉴权在 validate 阶段就拒了（校验异常先于 setAttribute）：拒绝路径没有 history 可补记，
        // 不判空会在 afterCompletion 里抛 NPE（Spring 只记日志，但每次拒绝都刷一条 ERROR）
        if (history == null) {
            return;
        }
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