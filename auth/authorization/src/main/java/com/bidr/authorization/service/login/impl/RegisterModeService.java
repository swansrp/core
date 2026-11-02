package com.bidr.authorization.service.login.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Title: RegisterModeService
 * Description: 自助注册模式（open/tenant/closed）的唯一读取点——注册闸、SSO 建号闸、租户绑定漏斗
 * （建号赋籍／登录补绑）与 ac_user 数据闸共用，避免同键多实现漂移。
 *
 * <p>唯一来源＝yml {@code my.auth.register-mode}：🔴 2026-11-02 用户裁定本键<b>不进 sys_config／参数页</b>，
 * 属部署定型配置——运行期不可热改，切换模式＝改 yml＋重启。缺省/空白/未知/大小写一律归一为 open
 * （共享兼容：不配置不得把存量开放注册项目突然锁死）。</p>
 *
 * <p>🔴 2026-11-02 另一条裁定：open＝全局账号形态，<b>不参与租户</b>——建号不赋默认租户、登录不补绑、
 * ac_user 数据闸整体旁路；tenant/closed 才走 {@code identity.tenant.code} 的默认租户赋籍与隔离。</p>
 *
 * @author Sharp
 * @since 2026-11-02
 */
@Service
public class RegisterModeService {

    public static final String MODE_OPEN = "open";
    public static final String MODE_TENANT = "tenant";
    public static final String MODE_CLOSED = "closed";

    /** 部署定型键：只认 yml，不读写 sys_config。缺省空——normalize 归一为 open。 */
    @Value("${my.auth.register-mode:}")
    private String registerMode;

    /** 测试注入用（离线单测不起 Spring）；包可见不构成对外入口。 */
    void setRegisterMode(String registerMode) {
        this.registerMode = registerMode;
    }

    /** 生效模式（open/tenant/closed，已归一）。 */
    public String current() {
        return normalize(registerMode);
    }

    /** open＝全局账号形态：不赋租户、不补绑、数据闸旁路。 */
    public boolean isOpen() {
        return MODE_OPEN.equals(current());
    }

    /**
     * 三态归一（离线可测）：tenant/closed 之外的一切取值（含空、未知、大小写、空白）回落 open
     * ——共享兼容口径：未配置或写错不得把存量开放注册项目突然锁死。
     */
    public static String normalize(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        return MODE_TENANT.equals(v) || MODE_CLOSED.equals(v) ? v : MODE_OPEN;
    }
}
