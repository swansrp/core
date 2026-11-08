package com.bidr.feishu.service.sso;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.dict.common.BoolDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.lark.oapi.Client;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReq;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenReqBody;
import com.lark.oapi.service.authen.v1.model.CreateAccessTokenResp;
import com.lark.oapi.service.contact.v3.model.GetUserReq;
import com.lark.oapi.service.contact.v3.model.GetUserResp;
import com.bidr.feishu.config.FeishuProperties;
import com.bidr.feishu.constant.FeishuUrl;
import com.bidr.feishu.dto.sso.FeishuSsoUser;
import com.bidr.feishu.service.contact.FeishuDepartmentService;
import com.bidr.feishu.util.FeishuMobileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Title: FeishuSsoService
 * Description: 飞书单点登录服务：授权页地址生成、code 换取用户信息、匹配/注册账号并签发系统登录态
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/01/01 00:00
 */
@Slf4j
@ConditionalOnProperty(prefix = "feishu", name = "app-id")
@ConditionalOnExpression("'${feishu.direct:false}' == 'true'")
@Service
public class FeishuSsoService {

    @Resource
    private FeishuProperties feishuProperties;

    @Resource
    private Client feishuClient;

    @Resource
    private AcAccountService acAccountService;

    @Resource
    private LoginService loginService;

    /**
     * 构建飞书网页授权页地址（前端跳转）
     *
     * @param state 回跳来路（hash 内部路径，如 /pan?folderId=3）：飞书授权后原样回带，
     *              nginx 将回跳 query 透传进前端 hash 路由，回调页据此恢复原页面；
     *              空则保留 pan 标识（飞书 state 上限 128 字节，超长由前端改走 sessionStorage 兜底）
     */
    public String getAuthorizeUrl(String state) {
        try {
            String stateParam = FuncUtil.isEmpty(state) ? "pan"
                    : URLEncoder.encode(state, StandardCharsets.UTF_8.name());
            return feishuProperties.getHost() + FeishuUrl.AUTHORIZE_PAGE
                    + "?app_id=" + feishuProperties.getAppId()
                    + "&redirect_uri=" + URLEncoder.encode(feishuProperties.getRedirectUri(),
                    StandardCharsets.UTF_8.name())
                    + "&state=" + stateParam;
        } catch (UnsupportedEncodingException e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "构建飞书授权地址失败");
        }
    }

    /**
     * 飞书 SSO 登录：code → user_access_token → open_id 匹配账号 → 签发系统登录态
     *
     * @param code 飞书授权回调的临时授权码
     * @return 系统登录信息
     */
    public LoginRes ssoLogin(String code) {
        FeishuSsoUser feishuUser = getUserByCode(code);
        // 账号身份键统一为飞书 user_id（与同步链路一致），取不到回退 open_id
        String resolved = resolveUserName(feishuUser.getOpenId());
        String identity = FuncUtil.isEmpty(resolved) ? feishuUser.getOpenId() : resolved;
        AcAccount account = acAccountService.selectById(identity);
        if (FuncUtil.isEmpty(account)) {
            // 未在基础库中的飞书用户，按授权信息注册账号
            account = buildAccount(feishuUser, identity);
            acAccountService.insert(account);
            log.info("飞书登录注册新账号, id: {}, name: {}", account.getId(), account.getName());
        }
        return loginService.loginOrReg(account);
    }

    /**
     * 查询飞书 user_id（employee_id，管理员在飞书后台分配的英文工号/拼音）
     *
     * @return 账号身份键与登录名共用值；未开通字段权限或该用户未分配时返回 null，由调用方回退 open_id
     * （open_id 为 ou_+32 位十六进制不可读，无法作为对外登录账号，仅作内部兜底）
     */
    private String resolveUserName(String openId) {
        try {
            GetUserResp resp = feishuClient.contact().v3().user().get(GetUserReq.newBuilder()
                    .userId(openId)
                    .userIdType("open_id")
                    .build());
            if (resp.success() && resp.getData() != null && resp.getData().getUser() != null
                    && FuncUtil.isNotEmpty(resp.getData().getUser().getUserId())) {
                return resp.getData().getUser().getUserId();
            }
            log.warn("查询飞书 user_id 为空，登录名回退 open_id, openId: {}", openId);
        } catch (Exception e) {
            log.warn("查询飞书 user_id 失败，登录名回退 open_id, openId: {}", openId, e);
        }
        return null;
    }

    /**
     * 用授权 code 换取用户信息
     */
    private FeishuSsoUser getUserByCode(String code) {
        CreateAccessTokenReq req = CreateAccessTokenReq.newBuilder()
                .createAccessTokenReqBody(CreateAccessTokenReqBody.newBuilder()
                        .grantType("authorization_code")
                        .code(code)
                        .build())
                .build();
        CreateAccessTokenResp resp;
        try {
            resp = feishuClient.authen().v1().accessToken().create(req);
        } catch (Exception e) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "飞书授权失败: " + e.getMessage());
        }
        if (!resp.success() || resp.getData() == null) {
            log.error("飞书授权换取用户信息失败, code: {}, msg: {}", code, resp.getMsg());
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "飞书授权失败: " + resp.getMsg());
        }
        FeishuSsoUser user = new FeishuSsoUser();
        user.setAccessToken(resp.getData().getAccessToken());
        user.setOpenId(resp.getData().getOpenId());
        user.setUnionId(resp.getData().getUnionId());
        user.setName(resp.getData().getName());
        user.setMobile(resp.getData().getMobile());
        user.setEmail(resp.getData().getEmail());
        user.setAvatarUrl(resp.getData().getAvatarUrl());
        return user;
    }

    /**
     * 按飞书用户信息构建账号
     *
     * @param identity 账号 id 与登录名（飞书 user_id 优先，已由调用方回退处理）
     */
    private AcAccount buildAccount(FeishuSsoUser feishuUser, String identity) {
        AcAccount account = new AcAccount();
        account.setId(identity);
        account.setName(feishuUser.getName());
        account.setUserName(identity);
        account.setMobile(FeishuMobileUtil.normalize(feishuUser.getMobile()));
        account.setEmail(feishuUser.getEmail());
        account.setPictureLink(feishuUser.getAvatarUrl());
        // 自动注册账号兜底挂飞书根部门：登录建用户时 bindDept 会以 department 写 ac_user_dept（dept_id 列非空），
        // 缺省即炸 SQL；同步路径均有部门，仅此路径漏设
        account.setDepartment(FeishuDepartmentService.ROOT_DEPT_ID);
        account.setEmployStatus(BoolDict.YES.getValue());
        account.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        account.setCreateAt(new Date());
        return account;
    }
}
