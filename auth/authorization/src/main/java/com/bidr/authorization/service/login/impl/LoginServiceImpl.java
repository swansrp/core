package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.bo.account.UserInfo;
import com.bidr.authorization.bo.account.UserPermitInfo;
import com.bidr.authorization.bo.account.UserRolePermitInfo;
import com.bidr.authorization.bo.role.RoleInfo;
import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.common.ClientType;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserTenant;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.join.AcUserRoleMenuService;
import com.bidr.authorization.holder.ClientTypeHolder;
import com.bidr.authorization.holder.TokenHolder;
import com.bidr.authorization.service.login.CustomerNumberHandler;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.login.QrCodeReq;
import com.bidr.authorization.vo.login.SsoLoginReq;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.*;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Title: LoginServiceImpl
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 11:51
 */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final AcUserService acUserService;
    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;
    private final SysConfigCacheService frameCacheService;
    private final TokenService tokenService;
    private final AcUserRoleMenuService acUserRoleMenuService;
    private final CreateUserService creatUserService;
    private final HttpServletRequest request;

    @Autowired(required = false)
    private CustomerNumberHandler customerNumberHandler;

    @Override
    public LoginRes login(AcUser user) {
        Validator.assertTrue(FuncUtil.equals(user.getStatus(), ActiveStatusDict.ACTIVATE.getValue()),
                AccountErrCode.AC_LOCK);
        return buildLoginRes(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, noRollbackFor = ServiceException.class)
    public LoginRes login(String loginId, String password) {
        AcUser user = acUserService.getUserByUserName(loginId);
        if (user == null) {
            user = acUserService.getUserByPhoneNumber(loginId);
        }
        if (user == null) {
            user = acUserService.getUserByEmail(loginId);
        }
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        Validator.assertTrue(FuncUtil.equals(user.getStatus(), ActiveStatusDict.ACTIVATE.getValue()),
                AccountErrCode.AC_LOCK);
        // 无密码账号处理
        if (FuncUtil.isEmpty(user.getPassword())) {
            if (user.getPasswordLastTime() != null && user.getPasswordLastTime().getTime() == 0L) {
                // 标记需要设置密码，走设置密码流程
                Validator.assertException(AccountErrCode.AC_PASSWORD_NOT_EXISTED);
            }
            // 无密码且没有设置密码标记，拒绝密码登录
            Validator.assertException(ErrCodeSys.SYS_ERR_MSG, "该账号未设置密码，请使用微信或短信验证码登录");
        }
        // 有密码，正常校验
        verifyPassword(user, user.getPassword(), password);
        // 密码过期检查
        Validator.assertNotNull(user.getPasswordLastTime(), AccountErrCode.AC_PASSWORD_EXPIRED);
        int expiredDays = frameCacheService.getParamInt(AccountParam.PASSWORD_EXPIRED);
        if(expiredDays > 0) {
            Date expiredTime = new Date(user.getPasswordLastTime().getTime() + expiredDays * 24 * 60 * 60 * 1000L);
            Validator.assertTrue(DateUtil.dayDiff(expiredTime, new Date()) > 0, AccountErrCode.AC_PASSWORD_EXPIRED);
        }
        return login(user);
    }

    private void verifyPassword(AcUser user, String passwordMd5, String password) {
        boolean verify = false;
        try {
            verify = Md5Util.verify(password, passwordMd5);
        } catch (Exception e) {
            Validator.assertException(AccountErrCode.AC_PASSWORD_LOGIN_FORMAT);
        }
        if (!verify) {
            int passwordMistakeMaxTime = frameCacheService.getParamInt(AccountParam.ACCOUNT_LOCK_MISTAKE_NUMBER);
            if(FuncUtil.isNotEmpty(user.getPasswordErrorTime())) {
                user.setPasswordErrorTime(user.getPasswordErrorTime() + 1);
            } else {
                user.setPasswordErrorTime(1);
            }
            if (user.getPasswordErrorTime() >= passwordMistakeMaxTime) {
                user.setStatus(ActiveStatusDict.LOCKING.getValue());
                acUserService.updateById(user);
                Validator.assertException(AccountErrCode.AC_PASSWORD_MAX_ERROR_TIMES);
            }
            acUserService.updateById(user);
            Validator.assertException(AccountErrCode.AC_PASSWORD_NOT_RIGHT,
                    String.valueOf(passwordMistakeMaxTime - user.getPasswordErrorTime()));
        } else {
            user.setPasswordErrorTime(0);
        }

    }

    @Override
    public LoginRes loginOrReg(String phoneNumber) {
        AcUser user = acUserService.getUserByPhoneNumber(phoneNumber);
        if (FuncUtil.isEmpty(user)) {
            user = creatUserService.createUserWithName(phoneNumber, phoneNumber);
        }
        return buildLoginRes(user);
    }

    @Override
    public LoginRes loginOrRegByWechatMiniOpenId(String wechatOpenId) {
        return loginOrRegByWechatPhoneNumber(wechatOpenId, null, null, null);
    }

    @Override
    public LoginRes loginOrRegByWechatUnionId(String wechatUnionId, String nickName, String avatar) {
        return loginOrRegByWechatPhoneNumber(wechatUnionId, nickName, null, avatar);
    }

    @Override
    public LoginRes loginOrRegByWechatPhoneNumber(String wechatId, String nickName, String phoneNumber, String avatar) {
        AcUser wechatUser = acUserService.getUserByWechatId(wechatId);
        AcUser phoneNumberUser = acUserService.getUserByPhoneNumber(phoneNumber);
        if (FuncUtil.isEmpty(wechatUser)) {
            if (FuncUtil.isNotEmpty(phoneNumberUser)) {
                wechatUser = creatUserService.mergeWechatPhoneNumber(wechatId, nickName, phoneNumberUser, avatar);
            } else {
                wechatUser = creatUserService.createUserFromWechat(wechatId, nickName, phoneNumber, avatar);
            }
        } else {
            if (FuncUtil.isNotEmpty(phoneNumberUser)) {
                if (!wechatUser.getUserId().equals(phoneNumberUser.getUserId())) {
                    wechatUser = creatUserService.mergeWechatPhoneNumber(wechatId, nickName, phoneNumberUser, avatar);
                } else {
                    // 同一用户重复绑定 同样需要刷新customerNumber
                    fillBindPhoneNumber(wechatUser, phoneNumber);
                }
            } else {
                // 有手机号但是没有手机号用户 说明是微信手机号绑定
                fillBindPhoneNumber(wechatUser, phoneNumber);
            }
        }
        return buildLoginRes(wechatUser);
    }

    private void fillBindPhoneNumber(AcUser wechatUser, String phoneNumber) {
        if (FuncUtil.isEmpty(phoneNumber)) {
            return;
        }
        wechatUser.setPhoneNumber(phoneNumber);
        wechatUser.setUserName(phoneNumber);
        // customerNumber生成规则由项目CustomerNumberHandler决定 未定义时保持序列号不变
        if (FuncUtil.isNotEmpty(customerNumberHandler)) {
            String customerNumber = customerNumberHandler.getCustomerNumber(wechatUser);
            if (FuncUtil.isNotEmpty(customerNumber)) {
                wechatUser.setCustomerNumber(customerNumber);
            }
        }
    }

    @Override
    public LoginRes refreshLogin(String refreshToken) {
        TokenInfo tokenInfo = AuthTokenUtil.decode(refreshToken);
        Validator.assertTrue(tokenService.isTokenExist(tokenInfo), ErrCodeSys.SYS_SESSION_TIME_OUT);
        String customerNumber = tokenService.getItem(tokenInfo, TokenItem.OPERATOR.name(), String.class);
        Validator.assertNotBlank(customerNumber, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        Validator.assertNotEquals(user.getStatus(), ActiveStatusDict.LOCKING.getValue(), AccountErrCode.AC_LOCK);
        tokenService.removeToken(tokenInfo);
        return buildLoginRes(user);
    }

    /**
     * 旧兼容签名（无租户码）：委托经 this 自调用<b>不过 Spring 代理</b>，故不走事务——
     * 与历史实现（原 register 也无事务注解）语义一致；业务入口请用带 tenantCode 的重载。
     */
    @Override
    public LoginRes register(String userName, String password) {
        return this.register(userName, password, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRes register(String userName, String password, String tenantCode) {
        AcTenant tenant = assertRegisterAllowed(tenantCode);
        AcUser user = acUserService.getUserByUserName(userName);
        Validator.assertNull(user, AccountErrCode.AC_USER_ALREADY_EXISTED);
        user = creatUserService.createUser(userName, password, userName, null, null, null);
        bindRegisterTenant(user.getUserId(), tenant);
        return buildLoginRes(user);
    }

    @Override
    public LoginRes registerWithEmail(String userName, String password, String email) {
        // 语义是"注册"不是"登录"：同样过三态闸（closed 拒；tenant 态无码拒），防成 closed 态的漏网建号口
        AcTenant tenant = assertRegisterAllowed(null);
        AcUser user = acUserService.getUserByUserName(userName);
        Validator.assertNull(user, AccountErrCode.AC_USER_ALREADY_EXISTED);
        user = creatUserService.createUser(userName, password, userName, null, email, null);
        bindRegisterTenant(user.getUserId(), tenant);
        return buildLoginRes(user);
    }

    /**
     * 旧兼容签名（无租户码）：委托经 this 自调用<b>不过 Spring 代理</b>，故不走事务——
     * 与历史实现语义一致；业务入口请用带 tenantCode 的重载。
     */
    @Override
    public LoginRes registerWithPhoneNumber(String userName, String password, String phoneNumber) {
        return this.registerWithPhoneNumber(userName, password, phoneNumber, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginRes registerWithPhoneNumber(String userName, String password, String phoneNumber, String tenantCode) {
        AcTenant tenant = assertRegisterAllowed(tenantCode);
        AcUser user = acUserService.getUserByUserName(userName);
        Validator.assertNull(user, AccountErrCode.AC_USER_ALREADY_EXISTED);
        user = creatUserService.createUser(userName, password, userName, phoneNumber, null, null);
        bindRegisterTenant(user.getUserId(), tenant);
        return buildLoginRes(user);
    }

    /** 自助注册三态取值 */
    private static final String REGISTER_MODE_OPEN = "open";
    private static final String REGISTER_MODE_TENANT = "tenant";
    private static final String REGISTER_MODE_CLOSED = "closed";

    /**
     * 三态归一（离线可测）：tenant/closed 之外的一切取值（含空、未知、大小写、空白）回落 open
     * ——共享兼容口径：参数缺行或写错不得把存量开放注册项目突然锁死。
     */
    static String normalizeRegisterMode(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        return REGISTER_MODE_TENANT.equals(v) || REGISTER_MODE_CLOSED.equals(v) ? v : REGISTER_MODE_OPEN;
    }

    /**
     * 自助注册三态闸（ACCOUNT_REGISTER_MODE，读 sys_config 运行值）：closed 拒绝一切自助注册；
     * tenant 模式码必填且须命中有效租户（getByCode 只回 valid='1' 行）；open 模式码选填、给了同样
     * 校验并挂（/register/CODE 链接在 open 态同样可用）。返回 null＝注册为全局账号不挂租户。
     * 🔴 闸在服务端：前端隐藏入口只是形态，直打 API 缺码/错码同样在此被拒。
     * 包可见＝离线单测直断（LoginRegisterModeTest 的 mock 腿），不构成对外入口。
     */
    AcTenant assertRegisterAllowed(String tenantCode) {
        String normalized = normalizeRegisterMode(
                frameCacheService.getParamValueAvail(AccountParam.ACCOUNT_REGISTER_MODE.name()));
        if (REGISTER_MODE_CLOSED.equals(normalized)) {
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "自助注册已关闭，账号请联系管理员创建");
        }
        String trimmed = tenantCode == null ? null : tenantCode.trim();
        if (FuncUtil.isEmpty(trimmed)) {
            if (REGISTER_MODE_TENANT.equals(normalized)) {
                throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "当前仅支持租户注册，请通过租户注册链接进入");
            }
            return null;
        }
        AcTenant tenant = acTenantService.getByCode(trimmed);
        if (tenant == null) {
            // 不提"租户/码/停用"语义：不给试探者确认字段性质与存在性的信息面，统一按链接失效口径
            throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, "注册链接无效或已失效，请联系管理员获取");
        }
        return tenant;
    }

    /**
     * 注册即挂租户：is_admin 恒 false（租户管理员由成员管理面另行授予，注册入口不得自授管理员）。
     * 包可见＝离线单测直断 is_admin 针（不构成对外入口）。
     */
    void bindRegisterTenant(Long userId, AcTenant tenant) {
        if (tenant == null) {
            return;
        }
        AcUserTenant relation = new AcUserTenant();
        relation.setUserId(userId);
        relation.setTenantId(tenant.getId());
        relation.setIsAdmin(Boolean.FALSE);
        acUserTenantService.insert(relation);
    }

    @Override
    public LoginRes ghostLogin(String customerNumber) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        return login(user);
    }

    @Override
    public LoginRes loginByQrCode(QrCodeReq qrCodeReq) {
        // 验证扫码token有效性
        TokenInfo qrToken = AuthTokenUtil.resolveToken(qrCodeReq.getToken());
        Validator.assertNotNull(qrToken, ErrCodeSys.SYS_ERR_MSG, "二维码已过期，请重新扫码");
        Validator.assertTrue(tokenService.isTokenExist(qrToken), ErrCodeSys.SYS_ERR_MSG, "二维码已过期，请重新扫码");
        // 从token中解析customerNumber
        String customerNumber = qrToken.getCustomerNumber();
        Validator.assertNotBlank(customerNumber, ErrCodeSys.PA_DATA_NOT_EXIST, "用户");
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        // 扫码token使用后销毁
        tokenService.removeToken(qrToken);
        return login(user);
    }

    @Override
    public void emailRegister(String id, String email) {

    }

    @Override
    public LoginRes ssoLoginOrReg(SsoLoginReq ssoLoginReq) {
        return null;
    }

    @Override
    public LoginRes loginOrReg(AcAccount account) {
        AcUser user = acUserService.getByCustomerNumber(account.getId());
        if (FuncUtil.isEmpty(user)) {
            user = creatUserService.createUser(account);
        }
        return buildLoginRes(user);
    }

    @Override
    public void logoff() {
        tokenService.removeToken();
        tokenService.verifyToken(null);
    }

    private LoginRes buildLoginRes(AcUser user) {
        recordLogin(user);
        LoginRes res = ReflectionUtil.copy(user, LoginRes.class);
        fillToken(res, user);
        return res;
    }

    private void recordLogin(AcUser user) {
        user.setLoginDate(new Date());
        user.setLoginIp(HttpUtil.getRemoteIp(request));
        acUserService.updateById(user);
    }

    private void fillToken(LoginRes res, AcUser acUser) {
        TokenInfo accessToken = buildAccessToken(acUser.getUserId(), acUser.getCustomerNumber(), acUser.getName());
        TokenInfo refreshToken = buildRefreshToken(acUser.getCustomerNumber());
        res.setAccessToken(AuthTokenUtil.getToken(accessToken));
        if (FuncUtil.isNotEmpty(refreshToken)) {
            // 微信相关不存在refreshToken
            res.setRefreshToken(AuthTokenUtil.getToken(refreshToken));
        }
        String clientType = ClientTypeHolder.get();
        UserRolePermitInfo info = acUserRoleMenuService.getByCustomerNumberAndClientType(acUser.getCustomerNumber(),
                clientType);
        buildPermitTree(info, accessToken);
        buildAccountInfo(info, accessToken);
        buildTenantIds(acUser.getUserId(), accessToken);
        TokenHolder.set(accessToken);
    }

    /**
     * 登录签发时把用户租户集烤进 access token（承诺面：经 AcUserTenantService 现查，非缓存）。
     * <p>空集合＝不写入——AuthLogin.parseTenantIds 按缺键解析为 null＝旁路形态，
     * 老项目零感知口径与此一致。</p>
     */
    private void buildTenantIds(Long userId, TokenInfo accessToken) {
        List<Long> tenantIds = acUserTenantService.listTenantIdsByUserId(userId);
        if (FuncUtil.isNotEmpty(tenantIds)) {
            tokenService.putItem(accessToken, TokenItem.TENANT_IDS.name(), tenantIds);
        }
    }

    private TokenInfo buildAccessToken(Long userId, String customerNumber, String nickName) {
        TokenInfo accessToken = null;
        String clientType = ClientTypeHolder.get();
        if (FuncUtil.equals(clientType, ClientType.WEB.getValue())) {
            accessToken = tokenService.buildWebAccessToken(customerNumber);
        } else if (FuncUtil.equals(clientType, ClientType.APP.getValue())) {
            accessToken = tokenService.buildAppAccessToken(customerNumber);
        } else if (FuncUtil.equals(clientType, ClientType.WECHAT.getValue())) {
            accessToken = tokenService.buildWechatToken(customerNumber);
        } else if (FuncUtil.equals(clientType, ClientType.PUBLIC.getValue())) {
            accessToken = tokenService.buildWxPlatformToken(customerNumber);
        }
        tokenService.putItem(accessToken, TokenItem.USER_ID.name(), userId);
        tokenService.putItem(accessToken, TokenItem.OPERATOR.name(), customerNumber);
        tokenService.putItem(accessToken, TokenItem.NICK_NAME.name(), nickName);
        return accessToken;
    }

    private TokenInfo buildRefreshToken(String customerNumber) {
        TokenInfo refreshToken = null;
        String clientType = ClientTypeHolder.get();
        if (clientType.equals(ClientType.APP.getValue())) {
            refreshToken = tokenService.buildAppRefreshToken(customerNumber);
        } else if (clientType.equals(ClientType.WEB.getValue())) {
            refreshToken = tokenService.buildWebRefreshToken(customerNumber);
        }
        return refreshToken;
    }

    private void buildPermitTree(UserPermitInfo info, TokenInfo accessToken) {
        if (FuncUtil.isNotEmpty(info.getMenuList())) {
            tokenService.putItem(accessToken, TokenItem.PERMIT_TREE.name(), info.getMenuList());
        }
    }

    private void buildAccountInfo(UserRolePermitInfo info, TokenInfo accessToken) {
        Map<Long, RoleInfo> role = ReflectionUtil.reflectToMap(info.getRoleInfoList(), RoleInfo::getRoleId);
        tokenService.putItem(accessToken, TokenItem.USER_INFO.name(), ReflectionUtil.copy(info, UserInfo.class));
        tokenService.putItem(accessToken, TokenItem.ROLE_MAP.name(), role);
    }
}
