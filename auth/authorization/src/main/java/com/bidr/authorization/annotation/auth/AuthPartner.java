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
import com.bidr.kernel.config.response.ApiResultStatus;
import com.bidr.kernel.config.response.Response;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
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
        // status 闸（fail-closed）：照 OpenApiLoginServiceImpl#getToken 先例——命中行后先判启用
        // 再比秘钥。停用的 partner 即便秘钥比对通过也不放行，且以 1027 直陈「渠道不可用」，
        // 不让后续秘钥比对用 1030 掩盖停用事实。
        if (!ActiveStatusDict.ACTIVATE.getValue().equals(acPartner.getStatus())) {
            // inline 补写审计：validate 抛异常后 completion 不会执行（history 属性未挂、判空早退），
            // 停用拒绝若不在此处落账，这一枪就无痕。先记后拒：status/responseAt/message 填拒绝
            // 语义，落库形态与 completion 同构（ServiceException → HTTP 200 + 错误码信封）。
            AcPartnerHistory rejected = buildPartnerHistory(request, acPartner);
            rejected.setStatus(HttpServletResponse.SC_OK);
            rejected.setResponseAt(new Date());
            rejected.setMessage(JsonUtil.toJson(new ApiResultStatus(AccountErrCode.AC_PARTNER_NOT_AVAILABLE.getErrCode(),
                    AccountErrCode.AC_PARTNER_NOT_AVAILABLE.getErrMsg(), null), false, false, true));
            // 审计尽力而为（S-1）：inline 落库失败不得劫持下面的 1027 拒绝契约——
            // 否则干净的「渠道不可用」业务信封劣化为系统错误信封，审计意图也未达成
            // （异常连 log 都没有）。记 ERROR 后继续走拒绝，与 completion
            // 「审计失败不影响响应」的容忍度对齐。
            try {
                acPartnerHistoryService.insert(rejected);
            } catch (Exception e) {
                log.error("停用 partner 拒绝审计落库失败", e);
            }
            Validator.assertEquals(acPartner.getStatus(), ActiveStatusDict.ACTIVATE.getValue(),
                    AccountErrCode.AC_PARTNER_NOT_AVAILABLE);
        }
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
        Map<String, Object> map = new HashMap<>(4);
        map.put(TokenItem.PLATFORM.name(), acPartner.getPlatform());
        // appKey 烤入上下文：IdP 账密换票端点取它作签发 claim 的 app_id（票的受众接入方）
        map.put(TokenItem.APP_KEY.name(), acPartner.getAppKey());
        // partnerId 烤入上下文：开放口控制器按 ac_subsystem.partner_id 解析菜单归属应用
        // （app_code）——机器凭证现场已持有 ac_partner 整行，id 顺手烤入免二次查表
        map.put(TokenItem.PARTNER_ID.name(), acPartner.getId());
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