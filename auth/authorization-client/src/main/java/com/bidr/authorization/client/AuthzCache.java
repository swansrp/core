package com.bidr.authorization.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: AuthzCache
 * Description: client 判定缓存——key=(userId, scope, authzVersion)，值=判定结果布尔。
 * 版本不符即整体失效（T2.3b：version 从 server 拉取并本地暂存，变更周期=两次 Check 间隔）。
 * <p>
 * 🔴 无 TTL 兜底故意不做：fail 策略由 {@link AuthzClientProperties} 决定，
 * 缓存失效的唯一正源是 authz_version bump（§8.1 client 判定链路②）。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public class AuthzCache {

    private volatile long version = -1;
    private final Map<String, Boolean> decisions = new ConcurrentHashMap<>();

    /** 版本一致则查缓存；版本已 bump 即清空（秒级收回语义） */
    public Boolean get(long currentVersion, Long userId, String scope, String permission) {
        if (currentVersion != version) {
            decisions.clear();
            version = currentVersion;
        }
        return decisions.get(key(userId, scope, permission));
    }

    public void put(long currentVersion, Long userId, String scope, String permission, boolean allow) {
        if (currentVersion != version) {
            decisions.clear();
            version = currentVersion;
        }
        decisions.put(key(userId, scope, permission), allow);
    }

    /** 测试与运维用：强制清空 */
    public void invalidate() {
        decisions.clear();
    }

    public int size() {
        return decisions.size();
    }

    private String key(Long userId, String scope, String permission) {
        return userId + "|" + scope + "|" + permission;
    }
}
