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
     * 用户名密码注册.
     *
     * @param loginId   用户名
     * @param password 密码
     * @return 登录返回信息
     */
    LoginRes register(String loginId, String password);

    /**
     * 用户名密码邮箱注册.
     *
     * @param loginId   用户名
     * @param password 密码
     * @param email    邮箱
     * @return 登录返回信息
     */
    LoginRes register(String loginId, String password, String email);

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
