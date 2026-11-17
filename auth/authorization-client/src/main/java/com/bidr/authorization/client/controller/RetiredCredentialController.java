package com.bidr.authorization.client.controller;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthNone;
import com.bidr.authorization.client.login.SubsystemLoginService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: RetiredCredentialController
 * Description: 已退役本地凭据端点的统一墓碑（子系统接入形态通用件）——
 * 接主系统（墨枢）SSO 的子系统没有自己的 ac_* 凭据面（账号归主系统，本地只落 idm_* 镜像），
 * 密码/短信/注册/代登录一族端点在页面上不可达；保留拒绝面只为让误触得到明确业务提示
 * （「统一使用主系统账号登录」）而非 404。
 *
 * <p>🔴 2026-11-17 起 {@code /web/login} 不在本表：账密登录已上提为 client 标准能力
 * （{@code SubsystemPasswordLoginController}，带 {@code @CaptchaVerify} 前置，未配机器凭证时
 * 回同一墓碑文案）；本类只接住其余 8 条无实现路径。</p>
 *
 * <p>🔴 互斥显式化（条件直接挂类上——本件与 {@code TokenController} 同款靠宿主
 * {@code @ComponentScan("com.bidr")} 扫描装配，不走 AutoConfiguration，条件挂 AutoConfig
 * 类上拦截不到扫描路径）：authorization(server) 真实现在 classpath（主系统/InkHub 形态）
 * ＝本墓碑不注册，真 {@code /web/login} 独占 URL；子系统形态（identity-mirror，无 server
 * 件）＝注册墓碑接住误触。两件同 classpath 的乱配形态下真实现赢，不双 mapping 撞车。</p>
 *
 * <p>开关 {@code my.authorization.retired-credential.enabled}（缺省 true）：未来某接入方
 * 要自建本地凭据面时关掉墓碑让位（关闭前先确认对应 URL 有了自己的实现，否则回 404）。</p>
 *
 * @author Sharp
 * @since 2026/11/05
 */
@RestController("RetiredCredentialController")
@ConditionalOnMissingClass("com.bidr.authorization.controller.login.LoginController")
@ConditionalOnProperty(prefix = "my.authorization.retired-credential", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RetiredCredentialController {

    /** 与子系统登录服务同源（唯一正源：{@link SubsystemLoginService#RETIRED_MESSAGE}），防两处文案漂移 */
    static final String RETIRED_MESSAGE = SubsystemLoginService.RETIRED_MESSAGE;

    @Auth(AuthNone.class)
    @RequestMapping(value = {
            "/web/login/password/init",      // 初始密码
            "/web/login/password/change",    // 改密
            "/web/login/msg",                // 短信登录
            "/web/login/register",           // 密码注册
            "/web/login/register/msg",       // 短信注册
            "/web/password/change",          // 改密（另一路径）
            "/web/admin/ghost/login",        // 代登录
            "/web/sms"                       // 短信验证码下发
    }, method = {RequestMethod.POST, RequestMethod.GET})
    public void retired() {
        throw new ServiceException(ErrCodeSys.SYS_ERR_MSG, RETIRED_MESSAGE);
    }
}
