package com.bidr.authorization.constants;

/**
 * Title: AuthConstants
 * Description: 授权域常量正源（api 层）。内置管理员账号单一正源——
 * 原 {@code MenuSeeder#SYSTEM_USER}（service 面）外移至此，service/client/业务模块统一改引本类。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public final class AuthConstants {

    /** 内置管理员账号（单一正源；MenuSeeder 播种与系统通知缺省收件人共用） */
    public static final String SYSTEM_USER = "000001";

    private AuthConstants() {
    }
}
