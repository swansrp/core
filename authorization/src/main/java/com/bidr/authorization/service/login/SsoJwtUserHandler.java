package com.bidr.authorization.service.login;

import com.bidr.authorization.constants.SsoConfigKeys;
import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.authorization.utils.jwt.SsoJwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: SsoJwtUserHandler
 * Description: core 内置的自读票 SSO 身份解析 handler（M15 棒 0b-2 纯加法，子系统/plsintec 形态用）。
 * <p>
 * 与 bidr 形态的 IdmSsoUserHandler（线程态取身份）平级、互斥生效：本类从<b>当前请求</b>直接读
 * 墨枢自签的 id_token 并用配置的验票公钥（键名正源＝{@link SsoConfigKeys#JWT_PUBLIC_KEY}，
 * 🔴 子系统侧配置，墨枢自身永不声明该键）本地验签取用户名，全链路零 idm、零业务包依赖。
 * </p>
 * <p>
 * 🔴 装配语义（M-7 实测口径）：{@code @ConditionalOnProperty} 不带 havingValue＝<b>键存在即出生</b>
 * ——即使值配成空串也算存在，因此墨枢侧的纪律是"完全不声明该键"而非"声明成空串"。
 * 误配该键的后果分两形态（承诺面 42 的完整事实，G-9）：
 * <b>bidr 形态</b>（classpath 有 {@code IdmSsoUserHandler}）＝两个 handler 同时出生，
 * {@code getIfAvailable()} 抛 {@code NoUniqueBeanDefinitionException}→业务可见"实现冲突"拒绝、不崩启动；
 * <b>plsintec／子系统形态</b>（无 idm handler）＝<b>没有任何冲突信号</b>，该 handler 静默生效，
 * 误配的公钥会把"墨枢自签的票"变成打开墨枢本账号的通道——票经 URL 参数传（<b>最长 90 秒</b>内可从浏览器历史／
 * nginx access log 捞到＝ttl 60 秒＋exp 判定 30 秒偏移容忍的合成，不是 60 秒），且本批明确不做 {@code aud}（待拍 Q8）与 jti 吊销，捞到即能用。
 * 空串值的行为面兜底＝验票必 null，走既有"SSO身份源未返回用户身份"业务拒绝文案，不 NPE。
 * </p>
 * <p>
 * 🔴 取票顺序照现网 IdaaS 拦截器口径（M-3/M-17）：先请求参数 {@link RequestConst#ID_TOKEN}，
 * 空白才回落同名请求头；身份只能从当前请求取（{@code resolveUserName()} 无参），
 * {@link RequestContextHolder} 取用先例＝本模块 {@code annotation/alert/ExceptionAlertAspect}。
 * 验不过／过期（{@link SsoJwtUtil#verifyAndGetUsername} 内部自判 exp＝M-4）／公钥空白／拿不到请求
 * ⇒ 一律回 null 不抛——拒票语义由调用方（SSO 登录口）按文案处理。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-14
 */
@Slf4j
@Service
@ConditionalOnProperty(name = SsoConfigKeys.JWT_PUBLIC_KEY)
public class SsoJwtUserHandler implements SsoUserHandler {

    /**
     * 验票公钥（X.509 base64，容忍 PEM 头尾/空白）；键名经 {@link SsoConfigKeys} 常量拼接引用，
     * 磁盘只此一处声明。空默认仅为注入兜底——空白值在验票面一律收敛为 null。
     */
    private final String jwtPublicKey;

    public SsoJwtUserHandler(@Value("${" + SsoConfigKeys.JWT_PUBLIC_KEY + ":}") String jwtPublicKey) {
        this.jwtPublicKey = jwtPublicKey;
    }

    @Override
    public String resolveUserName() {
        try {
            HttpServletRequest request = currentRequest();
            if (request == null) {
                // 拿不到当前请求（非 web 线程调用等）视为身份未解析，不抛
                return null;
            }
            // 取序（M-3/M-17）：参数 id_token 优先，空白回落同名 header——不新造字面量
            String token = request.getParameter(RequestConst.ID_TOKEN);
            if (StringUtils.isBlank(token)) {
                token = request.getHeader(RequestConst.ID_TOKEN);
            }
            if (StringUtils.isBlank(token)) {
                return null;
            }
            // SsoJwtUtil 契约：验签+exp 判定，任何失败回 null 不抛（M-4/M-5）；公钥空白同样收敛为 null
            return SsoJwtUtil.verifyAndGetUsername(token, jwtPublicKey);
        } catch (Throwable t) {
            // 兜底收窄契约：handler 任何异常都不得打断 SSO 登录口，按"未解析出身份"处理
            // 🔴 只打异常简名（G-7 同族纪律）：toString() 会带上 message，而 message 可能回显入参串＝票内容进日志
            log.warn("[SSO验票]内置自读票 handler 解析失败，按未解析处理: {}", t.getClass().getSimpleName());
            return null;
        }
    }

    /** 从线程绑定的当前请求上下文取 request；取不到回 null（先例＝ExceptionAlertAspect#appendRequestInfo） */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : attributes.getRequest();
    }
}
