package com.bidr.authorization.authz;

import java.util.function.LongSupplier;

/**
 * Title: AuthzVersionHolder
 * Description: 授权版本正源（server 侧静态门面）——任何授权 CRUD 变更处调 {@link #bump()}；
 * client 的缓存键含版本，版本不符即整缓存失效（§8.1 秒级收回语义，无 TTL、无推送）。
 * <p>
 * 版本存储默认进程内（单实例/双 delivery 各自独立即够用）；集群部署时注入
 * {@link LongSupplier} 走 sys_config/Redis（DbAwareModelConfigProvider 三级回落先例），
 * 接口不变。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public final class AuthzVersionHolder {

    private static volatile long version = 1L;
    private static volatile LongSupplier clusterSource;

    private AuthzVersionHolder() {
    }

    /** 授权变更处调用：本进程版本 +1 */
    public static long bump() {
        return ++version;
    }

    /** 当前版本（client /authz/version 端点读这里；集群模式走注入源） */
    public static long current() {
        LongSupplier src = clusterSource;
        return src != null ? src.getAsLong() : version;
    }

    /** 集群模式注入（T2.3b 之后需要时接 Redis/sys_config，本版本不启用） */
    public static void setClusterSource(LongSupplier source) {
        clusterSource = source;
    }
}
