package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.vo.login.LoginRes;
import com.bidr.hub.spi.IdentityBridge;
import com.bidr.hub.spi.dto.BridgeUserPrincipal;
import com.bidr.hub.spi.dto.IdentityLoginCmd;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Title: IdentityBridgeLoginService
 * Description: SPI 登录装配（T1.1 棒4）——core 登录链消费 pack-spi {@link IdentityBridge} 的单点入口。
 * <p>
 * 装配两步（M-1 裁定：桥不签产品 JWT）：①桥验票——{@code bridge.login(cmd)} 回
 * {@link BridgeUserPrincipal}（外部身份纯数据）；②内核凭 principal.username 铸产品登录态——
 * 复用 {@link SsoLoginServiceImpl#issueFor(String)} 三分支装配（ac_user 命中/login、ac_account
 * 命中/loginOrReg、均无/漏斗建号后 login），不复制分支。
 * </p>
 * <p>
 * 🔴 ObjectProvider 空安全（plsintec 形态无 pack-bidr，无 IdentityBridge bean）：无桥 bean 时
 * 回落现 {@link SsoLoginServiceImpl} SsoUserHandler 链（M-15 保留），登录口不炸、启动不受影响；
 * 桥验票失败（含 IdmHolder 线程态无身份）按 pack-spi 契约由桥抛运行时异常，本服务统一兜底上浮。
 * 内核零身份源判据的可执行面：本类只认 SPI 接口，不 import 身份源包（idm starter，其包根字面量
 * 被 AuthCoreBoundaryConventionTest 禁成串，此处不写出）／不读 IdmHolder
 * （身份细节全部住 pack-bidr 桥内，守卫 bidrIdentitySourceImportsLiveOnlyInTheBridgeModule 钉死）。
 * </p>
 * <p>
 * 调用方：bidr 形态 AuthController（pack-bidr）POST /web/auth/login——对外契约零变化
 * （入参 id_token、出参 LoginRes）。
 * </p>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdentityBridgeLoginService {

    /**
     * SPI 缺席友好（与 SsoLoginServiceImpl 同口径）：getIfAvailable() 无实现 bean 返回 null 不抛；
     * 多候选且无 @Primary 抛 NoUniqueBeanDefinitionException，视为配置事故转业务拒绝。
     */
    private final ObjectProvider<IdentityBridge> identityBridgeProvider;
    private final SsoLoginServiceImpl ssoLoginService;

    /**
     * 票据换登录态：桥在则走 SPI 链（桥验票→principal→issueFor 铸态），桥不在回落
     * SsoUserHandler 链（身份源由各形态注册的 SsoUserHandler 提供）。
     *
     * @param cmd 登录命令（三态票据之一；bidr 形态为 ambient 形态，票据由桥内 IdmHolder 自解析）
     * @return 登录态与 token（与 /web/login 出参同形）
     */
    public LoginRes login(IdentityLoginCmd cmd) {
        IdentityBridge bridge = resolveBridge();
        if (bridge == null) {
            log.info("[身份桥登录]无 IdentityBridge 实现，回落 SsoUserHandler 链");
            return ssoLoginService.login();
        }
        BridgeUserPrincipal principal = bridge.login(cmd);
        Validator.assertNotBlank(principal == null ? null : principal.username, ErrCodeSys.SYS_ERR_MSG,
                "身份桥未返回用户身份，无法完成单点登录");
        return ssoLoginService.issueFor(principal.username);
    }

    /** SPI 取用：无候选＝null（回落腿），多候选无 @Primary＝业务拒绝（文案即根因） */
    private IdentityBridge resolveBridge() {
        try {
            return identityBridgeProvider.getIfAvailable();
        } catch (NoUniqueBeanDefinitionException e) {
            log.warn("[身份桥登录]IdentityBridge 实现冲突，候选 bean: {}", e.getBeanNamesFound());
            return Validator.assertException(ErrCodeSys.SYS_ERR_MSG,
                    "IdentityBridge 实现冲突（存在多个且无 @Primary），无法完成单点登录");
        }
    }
}
