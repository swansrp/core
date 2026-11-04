package com.bidr.authorization.service.login;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.authorization.vo.login.QrCodeReq;
import com.bidr.authorization.vo.login.SsoLoginReq;

/**
 * Title: LoginController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/04/26 16:09
 */
public interface LoginService {
    /**
     * 通过已有用户登录
     *
     * @param user 已有用户
     * @return
     */
    LoginRes login(AcUser user);

    /**
     * 用户名密码登录.
     *
     * @param loginId  用户标识(用户名或者手机号或者邮箱)
     * @param password 密码
     * @return 登录返回信息
     */
    LoginRes login(String loginId, String password);

    /**
     * 短信验证码登录.
     *
     * @param phoneNumber 手机号码
     * @return 登录返回信息
     */
    LoginRes loginOrReg(String phoneNumber);

    /**
     * 微信小程序登录.
     *
     * @param wechatOpenId 微信openId
     * @return 登录返回信息
     */
    LoginRes loginOrRegByWechatMiniOpenId(String wechatOpenId);

    /**
     * 微信登录.
     *
     * @param wechatUnionId 微信UnionId
     * @param nickName      微信昵称
     * @param avatar        头像
     * @return 登录返回信息
     */
    LoginRes loginOrRegByWechatUnionId(String wechatUnionId, String nickName, String avatar);

    /**
     * 微信登录.
     *
     * @param wechatId    微信id
     * @param nickName    微信您呈
     * @param phoneNumber 手机号码
     * @param avatar      头像
     * @return 登录返回信息
     */
    LoginRes loginOrRegByWechatPhoneNumber(String wechatId, String nickName, String phoneNumber, String avatar);


    /**
     * refreshToken登录.
     *
     * @param refreshToken 手机号码
     * @return 登录返回信息
     */
    LoginRes refreshLogin(String refreshToken);

    /**
     * 当前生效的自助注册模式（open/tenant/closed）：只读 yml {@code my.auth.register-mode}（缺省＝open），
     * 与注册闸共用同一读取点；登录页据此决定注册入口形态。🔴 不入 sys_config、不热改——切模式＝改 yml＋重启。
     *
     * @return 归一后的注册模式
     */
    String getRegisterMode();

    /**
     * 租户模式闸·建号前（SSO 首登）：yml {@code my.auth.register-mode=tenant/closed} 时禁止自动创建账号。
     *
     * <p>背景（2026-10-27 子系统联调）：SSO/IdM 首登查不到账号时原实现无条件自注册一个
     * <b>零租户归属</b>账号——空租户集在 core 又被解释为「系统管理级」旁路（可切任意租户、
     * 业务表不拼 tenant_id），形成跨租户读越权。租户模式下账号必须由管理员/主系统按租户分配后
     * 产生，不得先把人建出来再补归属。</p>
     *
     * <p>open＝保持历史行为（自动建号、无租户归属）；tenant＝拒绝并提示由管理员创建分配；
     * closed＝拒绝（注册面整体关闭）。</p>
     */
    void assertSsoAutoCreateAllowed();

    /**
     * 租户模式闸·登录前（SSO 已有账号）：tenant 模式下，SSO/REGISTER 类型账号必须至少有一个租户归属，
     * 否则拒绝发会话（防存量零归属账号继续以数据旁路形态进入）。
     *
     * <p>SYSTEM/ACCOUNT 类型（系统内部/主数据同步）不在此闸内——系统管理级账号的旁路语义保留。</p>
     *
     * @param user 已解析出的本地账号
     */
    void assertSsoTenantReady(AcUser user);

    /**
     * 用户名密码注册.
     *
     * @param loginId  用户名
     * @param password 密码
     * @return 登录返回信息
     */
    LoginRes register(String loginId, String password);

    /**
     * 用户名密码注册（带租户码）：租户注册模式（yml {@code my.auth.register-mode=tenant}）下码必填且须命中有效租户，
     * 注册即挂该租户（ac_user_tenant，is_admin=false）；open 模式选填、给了同样校验并挂。
     *
     * @param loginId    用户名
     * @param password   密码
     * @param tenantCode 租户码（可空）
     * @return 登录返回信息
     */
    LoginRes register(String loginId, String password, String tenantCode);

    /**
     * 用户名密码邮箱注册.
     *
     * @param loginId  用户名
     * @param password 密码
     * @param email    邮箱
     * @return 登录返回信息
     */
    LoginRes registerWithEmail(String loginId, String password, String email);

    /**
     * 用户名密码手机号注册.
     *
     * @param loginId     用户名
     * @param password    密码
     * @param phoneNumber 手机号
     * @return 登录返回信息
     */
    LoginRes registerWithPhoneNumber(String loginId, String password, String phoneNumber);

    /**
     * 用户名密码手机号注册（带租户码）：租户语义同 {@link #register(String, String, String)}。
     *
     * @param loginId     用户名
     * @param password    密码
     * @param phoneNumber 手机号
     * @param tenantCode  租户码（可空）
     * @return 登录返回信息
     */
    LoginRes registerWithPhoneNumber(String loginId, String password, String phoneNumber, String tenantCode);

    /**
     * 超管账户假借用户登录
     *
     * @param loginId
     * @return
     */
    LoginRes ghostLogin(String loginId);

    /**
     * 超管账户假借用户登录
     *
     * @param qrCodeReq 其他端登录信息
     * @return
     */
    LoginRes loginByQrCode(QrCodeReq qrCodeReq);

    /**
     * 利用邮箱注册账户
     *
     * @param id
     * @param email
     */
    void emailRegister(String id, String email);

    /**
     * 三方登录系统
     *
     * @param ssoLoginReq
     * @return
     */
    LoginRes ssoLoginOrReg(SsoLoginReq ssoLoginReq);

    /**
     * 通过已有白名单信息登录
     *
     * @param account 白名单账号
     * @return
     */
    LoginRes loginOrReg(AcAccount account);

    /**
     * 系统登出
     */
    void logoff();
}
