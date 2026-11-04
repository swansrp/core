package com.bidr.authorization.service.user.impl;

import com.bidr.authorization.constants.dict.DataPermitScopeDict;
import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.constants.err.AccountErrCode;
import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcTenant;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.entity.AcUserDept;
import com.bidr.authorization.dao.repository.AcTenantService;
import com.bidr.authorization.dao.repository.AcUserDeptService;
import com.bidr.authorization.dao.repository.AcUserTenantService;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.login.RoleBindService;
import com.bidr.authorization.service.login.impl.RegisterModeService;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.tenant.TenantResolver;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.kernel.validate.Validator;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Optional;

/**
 * Title: CreateUserServiceImpl
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/27 16:19
 */
@Service
@RequiredArgsConstructor
public class CreateUserServiceImpl implements CreateUserService {

    private final AcUserService acUserService;
    private final SysConfigCacheService sysConfigCacheService;
    private final RoleBindService roleBindService;
    private final AcUserDeptService acUserDeptService;
    private final TenantResolver tenantResolver;
    private final AcTenantService acTenantService;
    private final AcUserTenantService acUserTenantService;
    private final RegisterModeService registerModeService;

    /**
     * 租户行级隔离开关（M-2，与 TenantLineIsolationConfiguration 同键）：true＝租户交付形态，
     * 建号漏斗在 resolver 解析不到归属时拒建（fail-closed）；false＝非租户形态，解析空＝合法不绑。
     */
    @Value("${my.tenant.line-enabled:false}")
    private boolean tenantLineEnabled;

    /** 测试注入用（离线单测不起 Spring）；包可见不构成对外入口，风格同 LoginServiceImpl#setRegisterModeYml。 */
    void setTenantLineEnabled(boolean tenantLineEnabled) {
        this.tenantLineEnabled = tenantLineEnabled;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUserWithName(String phoneNumber, String name) {
        return createUser(phoneNumber, null, name, phoneNumber, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUser(String userId, String password) {
        return createUser(userId, password, userId, null, null, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUser(String loginId, String password, String name, String phoneNumber, String email,
                             String avatar) {
        // 租户赋籍判定在落库前：拒建（fail-closed）时零写入，注册/SSO JIT 链随事务整体回滚不留半行
        AcTenant tenantTarget = resolveCreateTenantTarget(loginId);
        AcUser user = buildBaseUser();
        user.setUserName(loginId);
        user.setPhoneNumber(phoneNumber);
        user.setPassword(Md5Util.generate(password));
        if (FuncUtil.isNotEmpty(password)) {
            // 注册链自带密码的号不算"未设过密码"，否则 PASSWORD_EXPIRED(365) 会把首登直接判成过期
            user.setPasswordLastTime(new Date());
        }
        user.setName(name);
        user.setNickName(name);
        user.setEmail(email);
        user.setAvatar(avatar);
        acUserService.insert(user);
        bindDefaultRole(user);
        bindCreateUserTenant(user.getUserId(), tenantTarget);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String changeAccountStatus(String customerNumber, Integer status) {
        AcUser user = acUserService.getByCustomerNumber(customerNumber);
        Validator.assertNotNull(user, AccountErrCode.AC_USER_NOT_EXISTED);
        user.setStatus(status);
        acUserService.updateById(user);
        return user.getName();
    }

    @Override
    public AcUser createUserFromWechat(String unionId, String nickName, String phoneNumber, String avatar) {
        AcUser user = buildBaseUser();
        user.setUserName(unionId);
        user.setWechatId(unionId);
        if (FuncUtil.isNotEmpty(phoneNumber)) {
            user.setPhoneNumber(phoneNumber);
        }
        if (FuncUtil.isNotEmpty(nickName)) {
            user.setNickName(nickName);
        }
        if (FuncUtil.isNotEmpty(avatar)) {
            user.setAvatar(avatar);
        }
        acUserService.insert(user);
        bindDefaultRole(user);
        return user;
    }

    @Override
    public AcUser mergeWechatPhoneNumber(String unionId, String nickName, AcUser phoneNumberUser, String avatar) {
        Validator.assertBlank(phoneNumberUser.getWechatId(), ErrCodeSys.SYS_ERR_MSG, "手机号账户已绑定微信");
        phoneNumberUser.setWechatId(unionId);
        if (StringUtils.isBlank(phoneNumberUser.getName())) {
            phoneNumberUser.setName(nickName);
        }
        acUserService.deleteByUserName(unionId);
        acUserService.updateById(phoneNumberUser);
        return phoneNumberUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AcUser createUser(AcAccount account) {
        // 同 6 参重载：拒建判定在落库前（MDM/飞书同步链批量调本重载，循环侧另有预检跳过）
        AcTenant tenantTarget = resolveCreateTenantTarget(account.getUserName());
        AcUser user = buildBaseUser();
        mergeAccount(user, account);
        acUserService.insert(user);
        bindDefaultRole(user);
        bindDept(account, user);
        bindCreateUserTenant(user.getUserId(), tenantTarget);
        return user;
    }

    /**
     * 建号租户赋籍判定（三链公共收口，T1.1 棒3）：MDM 同步、飞书同步两链汇入
     * {@link #createUser(AcAccount)}，SSO JIT／注册／手机号 loginOrReg 汇入 6 参重载——
     * 两处各自调本判定＋{@link #bindCreateUserTenant}，判据只此一份勿散改。
     * <ul>
     *   <li>🔴 open 注册模式 ⇒ 返回 null：open＝全局账号形态，不参与租户（2026-11-02 用户裁定），
     *       即便配了 {@code identity.tenant.code} 也不赋默认租户；</li>
     *   <li>resolver 命中 → 查 {@link AcTenantService#getByCode}：查无 ⇒ 抛
     *       {@link IllegalStateException}（配置说有租户而库里没有＝A/B 库漂移，宁可拒建）；</li>
     *   <li>解析空＋租户模式开（{@code my.tenant.line-enabled}）⇒ 抛异常拒建号
     *       （fail-closed，异常信息带 username 便于排查）；</li>
     *   <li>解析空＋租户模式关 ⇒ 返回 null＝非租户交付形态，合法不绑。</li>
     * </ul>
     */
    private AcTenant resolveCreateTenantTarget(String userName) {
        if (registerModeService.isOpen()) {
            return null;
        }
        Optional<String> code = tenantResolver.resolveTenantCode(userName);
        if (!code.isPresent()) {
            if (tenantLineEnabled) {
                throw new IllegalStateException("租户模式已开启（my.tenant.line-enabled）但身份[" + userName
                        + "]解析不到租户归属，拒绝建号（fail-closed）");
            }
            return null;
        }
        AcTenant tenant = acTenantService.getByCode(code.get());
        if (tenant == null) {
            throw new IllegalStateException("租户编码[" + code.get() + "]在 ac_tenant 无有效行（配置漂移），"
                    + "拒绝为身份[" + userName + "]建号");
        }
        return tenant;
    }

    /** 落籍收口：is_admin 恒 false（租户管理员只能由成员管理面另行授予）；target null＝非租户形态不绑。 */
    private void bindCreateUserTenant(Long userId, AcTenant tenantTarget) {
        if (tenantTarget == null) {
            return;
        }
        acUserTenantService.insertIfAbsent(userId, tenantTarget.getId(), Boolean.FALSE);
    }

    @Override
    public boolean isTenantAssignable(String userName) {
        try {
            resolveCreateTenantTarget(userName);
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private void mergeAccount(AcUser acUser, AcAccount account) {
        acUser.setDeptId(account.getDepartment());
        acUser.setUserName(account.getUserName());
        acUser.setCustomerNumber(account.getId());
        acUser.setName(account.getName());
        acUser.setPhoneNumber(account.getMobile());
        acUser.setEmail(account.getEmail());
        acUser.setSex(account.getGender());
        acUser.setAvatar(account.getPictureLink());
        acUser.setUserType(UserTypeDict.ACCOUNT.getValue());
    }

    private void bindDept(AcAccount account, AcUser user) {
        AcUserDept acUserDept = new AcUserDept();
        acUserDept.setUserId(user.getUserId());
        acUserDept.setDeptId(account.getDepartment());
        AcUserDept original = acUserDeptService.selectByMultiId(acUserDept);
        if (FuncUtil.isEmpty(original)) {
            acUserDept.setDataScope(DataPermitScopeDict.OWNER.getValue());
        } else {
            acUserDept.setDataScope(original.getDataScope());
        }
        acUserDeptService.deleteByMultiId(acUserDept);
        acUserDeptService.insert(acUserDept);
    }

    @Override
    public void bindDefaultRole(AcUser user) {
        Long defaultRoleId = sysConfigCacheService.getParamLong(AccountParam.ACCOUNT_DEFAULT_ROLE_ID);
        roleBindService.bindRole(user.getUserId(), defaultRoleId);
    }

    private AcUser buildBaseUser() {
        AcUser user = new AcUser();
        user.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        user.setPasswordErrorTime(0);
        // 0＝"尚未设过密码"标记：LoginServiceImpl 无密码分支凭它抛 AC_PASSWORD_NOT_EXISTED，登录页才翻「首次登录-设置密码」面板；
        // 置 now 会让无密码账号只能收到"请用微信或短信登录"，面板永不可达（erp F-4 实证）。
        user.setPasswordLastTime(new Date(0L));
        user.setValid(CommonConst.YES);
        return user;
    }
}
