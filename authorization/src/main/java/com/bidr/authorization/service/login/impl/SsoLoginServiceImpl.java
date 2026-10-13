package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.dict.UserTypeDict;
import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.account.AuthAccountService;
import com.bidr.authorization.service.login.LoginService;
import com.bidr.authorization.service.login.SsoUserHandler;
import com.bidr.authorization.service.user.CreateUserService;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.Md5Util;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Title: SsoLoginServiceImpl
 * Description: SSO 登录编排（core 底座自带，M15 棒 0 纯加法）。
 * <p>
 * 与框架侧 bidr 形态既有的 SSO 登录服务三分支逐支等价（ac_user 命中→login；
 * 未命中→主数据账号 ac_account 命中→loginOrReg；均无→建号并置 SSO 用户类型后 login），
 * 差异只在身份来源：不再直连某个具体 IdP 的线程态，而是经 {@link SsoUserHandler} SPI 解析。
 * </p>
 * <p>
 * 🔴 SPI bean 缺席（如 plsintec 等无 idm 的部署形态）不得 NPE、不得启动崩——
 * 经 {@link ObjectProvider#getIfAvailable()} 延迟解析（M15 棒0 C9 修订口径），
 * 三段拒绝文案按根因拆分（均为 {@code ErrCodeSys.SYS_ERR_MSG} 口径）：
 * 无候选＝"未接入SSO身份源"；多候选且无 {@code @Primary} 消歧＝"SsoUserHandler 实现冲突"；
 * handler 在但 {@code resolveUserName()} 空白/为 null＝"SSO身份源未返回用户身份（id_token 缺失或未通过上游验签）"。
 * </p>
 * <p>
 * 取用策略实证（本机 Spring 5.3 实测）：{@code orderedStream()} 多候选时<b>不</b>按 {@code @Primary} 去重
 * （size 仍为 2），会把"已用 @Primary 消歧"的合法配置一并拒掉；{@code getIfAvailable()} 认 {@code @Primary}，
 * 无唯一候选时抛 {@link NoUniqueBeanDefinitionException}，故本类按异常分流冲突腿。
 * </p>
 * <p>
 * 🔴 窄捕获口径（review#3 S-1）：只有 {@link NoUniqueBeanDefinitionException} 转业务拒绝。SPI bean
 * 自身装配故障（{@code BeanCreationException} 等）与 {@code resolveUserName()} 抛出的运行时异常
 * <b>故意不接</b>——按框架口径上浮为系统错误。把它们伪装成"实现冲突"是给配置事故撒谎（真实根因会被
 * 文案掩盖）；{@code AuthCoreBoundaryConventionTest} 之外的 {@code SsoLoginServiceImplTest} 钉了这条边界。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoLoginServiceImpl {

    private final LoginService loginService;
    private final AuthAccountService accountService;
    private final CreateUserService createUserService;
    private final AcUserService acUserService;
    /**
     * SPI 缺席友好：getIfAvailable() 在无实现 bean 时返回 null（不抛、不阻断启动）；
     * 多候选且无 @Primary 时抛 NoUniqueBeanDefinitionException，视为配置事故转业务拒绝（M15 棒0 C9）。
     */
    private final ObjectProvider<SsoUserHandler> ssoUserHandlerProvider;

    public LoginRes login() {
        SsoUserHandler ssoUserHandler = resolveHandler();
        String userName = ssoUserHandler.resolveUserName();
        Validator.assertNotBlank(userName, ErrCodeSys.SYS_ERR_MSG,
                "SSO身份源未返回用户身份（id_token 缺失或未通过上游验签），无法完成单点登录");
        AcUser acUser = acUserService.getUserByUserName(userName);
        if (acUser == null) {
            log.info("新用户 {} 查询主数据, 创建新用户", userName);
            AcAccount account = accountService.getAccountByUserName(userName);
            if (account == null) {
                log.info("用户 {} 不在主数据名单中, 创建新用户", userName);
                AcUser newUser = createUserService.createUser(userName, Md5Util.MD5(userName));
                newUser.setUserType(UserTypeDict.SSO.getValue());
                return loginService.login(newUser);
            } else {
                return loginService.loginOrReg(account);
            }
        } else {
            return loginService.login(acUser);
        }
    }

    /**
     * SPI 取用（M15 棒0 C9）：无候选与多候选无法消歧两腿各自业务拒绝，文案即根因，互不串台。
     *
     * @return 唯一生效的身份源实现（不会返回 null）
     */
    private SsoUserHandler resolveHandler() {
        SsoUserHandler handler;
        try {
            handler = ssoUserHandlerProvider.getIfAvailable();
        } catch (NoUniqueBeanDefinitionException e) {
            log.warn("[SSO登录]SsoUserHandler 实现冲突，候选 bean: {}", e.getBeanNamesFound());
            return Validator.assertException(ErrCodeSys.SYS_ERR_MSG,
                    "SsoUserHandler 实现冲突（存在多个且无 @Primary），无法完成单点登录");
        }
        Validator.assertNotNull(handler, ErrCodeSys.SYS_ERR_MSG,
                "未接入SSO身份源（SsoUserHandler 实现未注册），无法完成单点登录");
        return handler;
    }
}
