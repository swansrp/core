package com.bidr.authorization.service.login;

import com.bidr.authorization.bo.token.TokenInfo;
import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.authorization.dao.repository.AcUserService;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.authorization.utils.token.AuthTokenUtil;
import com.bidr.authorization.vo.login.TokenIntrospectRes;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.DateUtil;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.Map;

/**
 * Title: SsoTokenIntrospectService
 * Description: Token 回查（introspection）判定逻辑（M15 棒 0 纯加法，逻辑自
 * {@code TokenIntrospectController} 抽出，便于纯 Mockito 单测覆盖各腿）。
 * <p>
 * 三态语义：
 * <ol>
 *   <li>坏 token 串：{@link AuthTokenUtil#decode(String)} 自身抛 PA_PARAM_FORMAT 业务拒绝（不 catch，属预期）；
 *       <b>可解码但 type 段未知</b>（{@code TokenType.of} 返回 null）亦归此态——在进入
 *       {@code verifyToken} 前以 PA_PARAM_FORMAT「安全令牌」拒绝，杜绝 getKey→getPrefix 的 NPE；</li>
 *   <li>可解析但无效（不存在于服务端会话 / 游客态 / 已注销 / 归属不符）：valid=false；</li>
 *   <li>有效登录态：valid=true。</li>
 * </ol>
 * 分层判据（已拍板）：三态判定人人可查；身份明细仅当<b>调用方自身</b>带有效登录态才填充。
 * AuthNone 端点不会自动建立 token 上下文，故调用方登录态＝手搓
 * {@link AuthTokenUtil#extractToken(HttpServletRequest)} + verifyToken，不依赖线程态。
 * 调用方头容错边界（M15 棒0 C13 收窄口径）＝仅业务拒绝（{@link ServiceException}，
 * reviewer#2 已证 caller 段合法异常仅此一类）降级为未登录，其余异常照常上浮；target 腿保持坏串→业务拒绝契约。
 * </p>
 * <p>剩余寿命：RedisService 接口只写不读 TTL（无 getExpire/keyExpire 类方法），
 * 故按会话 map 的 {@code TIMESTAMP}+{@code EXPIRED} 推算（口径与 TokenServiceImpl 续期逻辑同源）。</p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SsoTokenIntrospectService {

    private final TokenService tokenService;
    private final AcUserService acUserService;

    public TokenIntrospectRes introspect(String token, HttpServletRequest request) {
        // 坏串在此抛 PA_PARAM_FORMAT 业务拒绝（预期语义，不捕获）
        TokenInfo target = AuthTokenUtil.decode(token);
        // 可解码但 type 段未知（TokenType.of 命中不到枚举）同样归坏串态：
        // 前移到 verifyToken 之前拒绝，杜绝 getKey→getPrefix 取 tokenType.name() 的 NPE
        Validator.assertNotNull(target.getType(), ErrCodeSys.PA_PARAM_FORMAT, "安全令牌");
        TokenIntrospectRes res = new TokenIntrospectRes();
        res.setValid(tokenService.verifyToken(target));
        res.setTokenType(target.getType().name());
        // 调用方登录态自查（AuthNone 不建上下文，必须从请求头手抽）
        // 🔴 仅 caller 头容错且容错宽度收窄（C13）：只有业务拒绝 ServiceException（脏串非三段等）
        //    降级为未登录，三态判定照常返回、身份明细保持为空；其余异常照常上浮；target 腿不容错（维持坏串→业务拒绝契约）
        TokenInfo caller = null;
        boolean callerLogin = false;
        try {
            caller = AuthTokenUtil.extractToken(request);
            callerLogin = caller != null && tokenService.verifyToken(caller);
        } catch (ServiceException e) {
            log.warn("[token回查]调用方身份头解析失败，按未登录处理: {}", e.getMessage(), e);
        }
        if (res.isValid() && callerLogin) {
            res.setCustomerNumber(target.getCustomerNumber());
            AcUser acUser = acUserService.getByCustomerNumber(target.getCustomerNumber());
            if (acUser != null) {
                res.setUserId(acUser.getUserId());
                res.setName(acUser.getName());
            }
            res.setExpireSeconds(calcRemainSeconds(target));
            // C10 明细命中留痕：调用方身份 + 被查 token 前 8 位（不落全量 token）+ 命中用户
            log.info("[token回查]调用方: {}, 被查token: {}…, 命中用户: {}",
                    caller.getCustomerNumber(), StringUtils.substring(token, 0, 8), res.getUserId());
        }
        return res;
    }

    private Long calcRemainSeconds(TokenInfo target) {
        try {
            Map<String, Object> tokenMap = tokenService.getTokenValue(target);
            if (tokenMap == null || tokenMap.isEmpty()) {
                return null;
            }
            Object timestamp = tokenMap.get(TokenItem.TIMESTAMP.name());
            Object expired = tokenMap.get(TokenItem.EXPIRED.name());
            if (timestamp == null || expired == null) {
                return null;
            }
            Date loginTime = DateUtil.stringToDateTime(String.valueOf(timestamp), DateUtil.DATE_TIME_NORMAL);
            long expiredSeconds = Long.parseLong(String.valueOf(expired));
            long remain = expiredSeconds - (System.currentTimeMillis() - loginTime.getTime()) / 1000L;
            return Math.max(remain, 0L);
        } catch (Exception e) {
            log.warn("[token回查]剩余寿命计算失败: {}", e.getMessage());
            return null;
        }
    }
}
