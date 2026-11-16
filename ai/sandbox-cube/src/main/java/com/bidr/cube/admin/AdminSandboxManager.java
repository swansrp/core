package com.bidr.cube.admin;

import com.bidr.cube.client.CubeHttpClient;
import com.bidr.cube.config.CubeSandboxProperties;
import com.bidr.kernel.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Title: AdminSandboxManager
 * Description: Cube 管理面沙箱生命周期管理——懒建一只<b>常驻管理沙箱</b>，专职在宿主侧
 * {@code mkdir -p} 出各会话的工作目录。为什么需要它：业务沙箱 create 时把
 * {@code {hostRoot}/{sessionId}} 作为 hostPath 挂载，而 <b>hostPath 目录不存在 ⇒ create
 * 直接 130545 失败（实测坑）</b>——因此建业务沙箱前必须先有宿主目录，而宿主上没有常驻
 * 进程可用，就借管理沙箱内 {@code mkdir -p} 完成（管理沙箱挂载 {@code {hostRoot}→{hostRoot}}，
 * 内外路径一致，mkdir 的路径即业务沙箱 hostPath，无需换算）。
 * <ul>
 *   <li>{@link #ensureDir(String)}：懒建管理沙箱（metadata 带 {@code role=cube-admin} 标记键，
 *       对账/清理不误伤业务会话；M-2：懒建前先按 role 对账复用既有实例——重启丢内存不重复
 *       建，列表异常降级直接 createAdmin＋warn）→ exec {@code mkdir -p {hostRoot}/{sessionId}}；
 *       exec 失败（管理沙箱已被平台 idle 回收/崩溃）⇒ 重建管理沙箱后<b>重试一次</b>（自愈）；
 *       再失败 ⇒ {@link ServiceException}；</li>
 *   <li>{@link #destroy()}：销毁管理沙箱（幂等：未建/已销毁静默；{@code client.delete} 的
 *       404 静默在客户端内）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026/11/16
 */
@Slf4j
public class AdminSandboxManager {

    /** 宿主沙箱根目录缺省值（与 CubeHttpClient.hostRoot 同口径；显式配置覆盖） */
    private static final String DEFAULT_HOST_ROOT = "/data/sandbox";

    private final CubeHttpClient client;

    private final CubeSandboxProperties properties;

    /** 常驻管理沙箱实例 id（懒建；null=未建/已销毁/已废弃） */
    private String adminSandboxId;

    public AdminSandboxManager(CubeHttpClient client, CubeSandboxProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    /**
     * 确保会话宿主目录存在（{@code {hostRoot}/{sessionId}}）：经管理沙箱 {@code mkdir -p}
     * 完成。exec 失败自愈一次（重建管理沙箱→重试），再失败抛 {@link ServiceException}。
     * 由 {@code CubeSandboxProvider.ensure} 在 create 之前调用（顺序红线）。
     */
    public synchronized void ensureDir(String sessionId) {
        String command = "mkdir -p " + shellQuote(hostRoot() + "/" + sessionId);
        if (execMkdir(command)) {
            return;
        }
        // 自愈：管理沙箱多半已被平台 idle 回收——废弃重建后再试一次
        log.warn("Cube 管理沙箱 mkdir 失败，重建管理沙箱后重试: cmd={}", command);
        discardAdminSandbox();
        if (execMkdir(command)) {
            return;
        }
        throw new ServiceException("Cube 管理沙箱预建宿主目录失败（重建重试后仍失败）: "
                + hostRoot() + "/" + sessionId);
    }

    /**
     * 销毁常驻管理沙箱（幂等：未建/已销毁静默；404 在 {@code client.delete} 内静默）。
     * 删除成功才清 id，失败保 id 供重试。
     */
    public synchronized void destroy() {
        String id = adminSandboxId;
        if (id == null) {
            return;
        }
        client.delete(id);
        adminSandboxId = null;
        log.info("Cube 管理沙箱已销毁: id={}", id);
    }

    // ---------------------------------------------------------------- 内部

    /** mkdir 一次尝试：成功（exit 0）返回 true；exec 异常/非零退出/超时一律 false（交自愈路径） */
    private boolean execMkdir(String command) {
        String id = lazyAdminSandboxId();
        if (id == null) {
            return false;
        }
        try {
            CubeHttpClient.ExecResult result = client.exec(id, command, adminExecTimeoutMs());
            return !result.timedOut() && result.exitCode() == 0;
        } catch (RuntimeException e) {
            // 典型：管理沙箱已被回收（exec 4xx/5xx/网络异常）——按失败处理走自愈
            log.warn("Cube 管理沙箱 exec 异常: id={} err={}", id, e.getMessage());
            return false;
        }
    }

    /**
     * 懒建管理沙箱：先按 {@code role=cube-admin} metadata 对账复用既有实例（M-2：进程重启
     * 丢内存后不重复建，与业务侧 D3 口径对称——管理沙箱是常驻单例，重复建会白占一台）；
     * 列表异常降级直接 createAdmin＋warn（行为不断）；createAdmin 失败直接上抛（平台不可用
     * 不是可自愈故障，不吞）。
     */
    private String lazyAdminSandboxId() {
        if (adminSandboxId == null) {
            adminSandboxId = reconcileOrCreateAdmin();
            log.info("Cube 管理沙箱已就绪: id={}（挂载 {}→{}）", adminSandboxId, hostRoot(), hostRoot());
        }
        return adminSandboxId;
    }

    /** role 对账（列表异常降级新建）＋createAdmin 兜底，取首个命中实例 */
    private String reconcileOrCreateAdmin() {
        try {
            List<String> ids = client.listSandboxIds("role", CubeHttpClient.ADMIN_METADATA_ROLE);
            if (!ids.isEmpty()) {
                log.info("Cube 管理沙箱对账命中既有实例: id={}", ids.get(0));
                return ids.get(0);
            }
        } catch (RuntimeException e) {
            log.warn("Cube 管理沙箱对账失败，降级直接新建: err={}", e.getMessage());
        }
        return client.createAdmin().sandboxId();
    }

    /** 废弃旧管理沙箱（自愈前置）：best-effort 删除（已不存在 404 静默在 client 内），失败仅 warn */
    private void discardAdminSandbox() {
        String id = adminSandboxId;
        if (id == null) {
            return;
        }
        adminSandboxId = null;
        try {
            client.delete(id);
        } catch (RuntimeException e) {
            log.warn("Cube 旧管理沙箱清理失败（忽略，继续重建）: id={} err={}", id, e.getMessage());
        }
    }

    /**
     * mkdir 的 exec HTTP 超时上限：复用 exec 缺省超时配置（秒→毫秒），clamp ≥1000ms（M-5：
     * 配置误填 0/负值不产生非正 Duration——HTTP client 会抛 IllegalArgumentException）；
     * mkdir 正常毫秒级，纯上限。
     */
    private long adminExecTimeoutMs() {
        return Math.max(1000L, properties.getTimeout() * 1000L);
    }

    /** 宿主沙箱根目录（去尾斜杠；与 CubeHttpClient.hostRoot 同口径） */
    private String hostRoot() {
        String hostRoot = properties.getHostRoot();
        if (hostRoot == null || hostRoot.isBlank()) {
            return DEFAULT_HOST_ROOT;
        }
        return hostRoot.endsWith("/") ? hostRoot.substring(0, hostRoot.length() - 1) : hostRoot;
    }

    /** shell 单引号包裹（sessionId 含特殊字符也不逃逸出 mkdir 参数位） */
    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
