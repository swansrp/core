package com.bidr.authorization.service.login.impl;

import com.bidr.authorization.constants.param.AccountParam;
import com.bidr.platform.dao.entity.SysConfig;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Title: RegisterModeService
 * Description: 自助注册模式（open/tenant/closed）的唯一读取点——注册闸、SSO 建号闸、租户绑定漏斗
 * （建号赋籍／登录补绑）与 ac_user 数据闸共用，避免同键多实现漂移。
 *
 * <p>三级链：① sys_config 库值（{@link AccountParam#ACCOUNT_REGISTER_MODE}，种子留空＝未表态）
 * → ② yml {@code my.auth.register-mode}（部署定型键）→ ③ 仍空＝open（共享兼容：参数写错不得把
 * 存量开放注册项目突然锁死）。库值必须走不抛断言的 getCache 读取（getParamValueAvail 在空值时
 * 抛 ServiceException，会让 yml 兜底腿永远到不了）。</p>
 *
 * <p>🔴 2026-11-02 用户裁定：open＝全局账号形态，<b>不参与租户</b>——建号不赋默认租户、登录不补绑、
 * ac_user 数据闸整体旁路；tenant/closed 才走 {@code identity.tenant.code} 的默认租户赋籍与隔离。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Service
@RequiredArgsConstructor
public class RegisterModeService {

    public static final String MODE_OPEN = "open";
    public static final String MODE_TENANT = "tenant";
    public static final String MODE_CLOSED = "closed";

    private final SysConfigCacheService sysConfigCacheService;

    /** 注册模式 yml 兜底：仅当 sys_config 库值未表态（空/空白）时轮到；缺省空——normalize 归一为 open。 */
    @Value("${my.auth.register-mode:}")
    private String ymlFallback;

    /** 测试注入用（离线单测不起 Spring）；包可见不构成对外入口。 */
    void setYmlFallback(String ymlFallback) {
        this.ymlFallback = ymlFallback;
    }

    /** 生效模式（open/tenant/closed，已归一）。 */
    public String current() {
        SysConfig row = sysConfigCacheService.getCache(AccountParam.ACCOUNT_REGISTER_MODE.name());
        String configured = row == null ? null : row.getConfigValue();
        // 空白也按"未表态"处理：FuncUtil.isEmpty 只判 null/长度 0，空白串会跳过 yml 腿
        if (configured == null || configured.trim().isEmpty()) {
            configured = ymlFallback;
        }
        return normalize(configured);
    }

    /** open＝全局账号形态：不赋租户、不补绑、数据闸旁路。 */
    public boolean isOpen() {
        return MODE_OPEN.equals(current());
    }

    /**
     * 三态归一（离线可测）：tenant/closed 之外的一切取值（含空、未知、大小写、空白）回落 open
     * ——共享兼容口径：参数缺行或写错不得把存量开放注册项目突然锁死。
     */
    public static String normalize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        return MODE_TENANT.equals(v) || MODE_CLOSED.equals(v) ? v : MODE_OPEN;
    }
}
