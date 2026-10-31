package com.bidr.authorization.client;

/**
 * Title: AuthzCheckApi
 * Description: server 侧 Check API 的 SDK 契约（T2.3b 由 client 实现 HTTP 版）。
 * server（authorization 模块）暴露 /authz/check /authz/menus /authz/version；
 * 本接口只描述"问一次判定"的最小面，client 缓存层在它之上做 version 键控。
 * <p>
 * 🔴 client 纪律：本模块禁依赖 authorization(server)；判定数据一律经本接口走 HTTP。
 *
 * @author Sharp
 * @since 2026-10-31
 */
public interface AuthzCheckApi {

    /**
     * 单点判定：user 在 scope 下是否持有 permission。
     *
     * @param userId     用户 ID（产品侧 ac_user.user_id）
     * @param scope      数据范围键（space/project 语义由 server 侧解释；传 "t:{tenantId}" 或 "p:{projectId}"）
     * @param permission 功能码（inkhub_app_function.function_code）
     * @return true=允许
     */
    boolean check(Long userId, String scope, String permission);

    /**
     * 当前授权版本号（server 侧任何授权 CRUD 变更即 bump）。
     * client 缓存键含本版本——版本不符即整缓存失效，不猜 TTL、不推消息。
     */
    long authzVersion();

    /**
     * 用户的菜单/功能码集（/authz/menus 的返回面）。
     *
     * @param userId 用户 ID
     * @return 功能码列表（空=无授权）
     */
    java.util.List<String> menus(Long userId);
}
