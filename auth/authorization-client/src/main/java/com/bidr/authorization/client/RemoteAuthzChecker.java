package com.bidr.authorization.client;

import com.bidr.authorization.authz.AuthzChecker;
import com.bidr.authorization.holder.AccountContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Title: RemoteAuthzChecker
 * Description: api SPI {@link AuthzChecker} 的远端形态实现（T2.3b）——
 * 平台管理员判定走 server 侧 /authz/check（InkHub 宿主端点），经
 * {@link AuthzCache} 版本键控缓存＋/authz/version 节流刷新。
 *
 * <p><b>与 server 侧 LocalAuthzChecker 的约定</b>：判定调用面＝
 * {@code check(userId, "", "admin")}（最小调用面：scope 空串＋约定权限码 "admin"——
 * server 侧 admin 短路腿在 scope 解析之前，空 scope 不影响 admin 判定；非 admin 用户
 * 空 scope 解析失败 fail-closed 返 false，语义＝"非管理员"，正是本判定所求）。</p>
 *
 * <p><b>判定链（plan §8.1，单测五腿钉死）</b>：</p>
 * <ol>
 *   <li>缓存命中零 HTTP（版本节流窗口内不重复拉 /authz/version）；</li>
 *   <li>server 侧 bump → 版本变化 → {@link AuthzCache} 整缓存失效 → 重拉；</li>
 *   <li>HTTP 失败 × {@code failMode=CLOSED}＝异常上抛（判定拒绝，fail-closed）；</li>
 *   <li>HTTP 失败 × {@code failMode=OPEN_TO_CACHE}＝回落最近一次成功判定
 *       （lastGood 面独立于 AuthzCache——版本 bump 清空主缓存后仍可回落）；无回落值同样上抛（拒）；</li>
 *   <li>版本刷新失败（HTTP 挂）不炸判定：沿用已知版本——同版本缓存命中即正确回答，
 *       缓存 miss 才进入 check 的 failMode 分支。</li>
 * </ol>
 *
 * <p>消费点：client 侧 {@code AuthAdmin}（@Auth(AuthAdmin) 端点 admin 门槛，ObjectProvider 注入，
 * SPI 缺失时 fail-closed 拒绝）。集群 bump 传播（setClusterSource）留既有钩子，本棒不启用。</p>
 *
 * @author coder (棒4, T2.3b 2026-11-03)
 * @since 2026-11-03
 */
@Slf4j
@RequiredArgsConstructor
public class RemoteAuthzChecker implements AuthzChecker {

    /** admin 判定的约定最小调用面：scope 空串＋权限码 admin（见类 javadoc 约定段） */
    static final String ADMIN_SCOPE = "";
    static final String ADMIN_PERMISSION = "admin";

    private final AuthzCheckApi authzCheckApi;
    private final AuthzCache authzCache;
    private final AuthzClientProperties properties;

    /** 最近一次成功判定（OPEN_TO_CACHE 回落面；版本 bump 清主缓存后仍保留） */
    private final Map<Long, Boolean> lastGoodAdmin = new ConcurrentHashMap<>();

    /** 已知授权版本（-1＝尚未拉到过；/authz/version 节流刷新） */
    private volatile long knownVersion = -1L;
    private volatile long lastVersionFetchAt = -1L;

    @Override
    public boolean isAdmin() {
        Long userId = AccountContext.getUserId();
        if (userId == null) {
            // 无登录态线程（未建上下文）：fail-closed 直接拒（语义同 AuthAdmin SPI 缺失）
            return false;
        }
        long version = currentVersionThrottled();
        Boolean cached = authzCache.get(version, userId, ADMIN_SCOPE, ADMIN_PERMISSION);
        if (cached != null) {
            return cached;
        }
        try {
            boolean allowed = authzCheckApi.check(userId, ADMIN_SCOPE, ADMIN_PERMISSION);
            authzCache.put(version, userId, ADMIN_SCOPE, ADMIN_PERMISSION, allowed);
            lastGoodAdmin.put(userId, allowed);
            return allowed;
        } catch (RuntimeException ex) {
            Boolean fallback = properties.getFailMode() == AuthzClientProperties.FailMode.OPEN_TO_CACHE
                    ? lastGoodAdmin.get(userId) : null;
            if (fallback != null) {
                log.warn("[authz]check 失败，OPEN_TO_CACHE 回落最近判定 userId={} admin={}",
                        userId, fallback, ex);
                return fallback;
            }
            // CLOSED＝上抛（拒绝）；OPEN_TO_CACHE 无回落值＝同样上抛（无缓存也拒）
            throw ex;
        }
    }

    /**
     * 版本刷新（节流）：窗口（versionRefreshMs）到点拉一次 /authz/version；
     * 拉取失败沿用已知版本（同版本缓存命中即正确回答，不因刷新失败误伤判定），
     * lastVersionFetchAt 只在成功时前移——失败后下一次判定立即重试。
     */
    private long currentVersionThrottled() {
        long now = System.currentTimeMillis();
        if (lastVersionFetchAt >= 0 && now - lastVersionFetchAt < properties.getVersionRefreshMs()) {
            return knownVersion;
        }
        try {
            long fetched = authzCheckApi.authzVersion();
            knownVersion = fetched;
            lastVersionFetchAt = now;
            return fetched;
        } catch (RuntimeException ex) {
            log.warn("[authz]/authz/version 刷新失败，沿用已知版本 {}（缓存命中不受影响）", knownVersion, ex);
            return knownVersion;
        }
    }
}
