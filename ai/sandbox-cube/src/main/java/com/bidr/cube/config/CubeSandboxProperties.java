package com.bidr.cube.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: CubeSandboxProperties
 * Description: Cube 沙箱底座的应用配置（{@code my.agent.sandbox.cube.*}）——
 * {@code CubeSandboxAutoConfiguration} 经 {@code @EnableConfigurationProperties} 注册，
 * kebab 键位映射：{@code api-url/api-key/data-base-url/template/host-root/allow-out/
 * admin-timeout/timeout}（Spring relaxed binding：{@code dataBaseUrl} ↔ {@code data-base-url}）。
 * <p>
 * 配置缺席语义：{@code apiUrl/apiKey/template} 空缺＝未接线，装配层打「未装配」行并让位
 * （不抛启动失败，与 OpenHands 腿「Bean 缺席自然降级」同口径）。
 *
 * @author Sharp
 * @since 2026/11/16
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "my.agent.sandbox.cube")
public class CubeSandboxProperties {

    /**
     * Cube 平台管理面地址（如 http://127.0.0.1:8080）；留空=未接线（装配让位）
     */
    private String apiUrl;

    /**
     * Cube 平台管理面密钥
     */
    private String apiKey;

    /**
     * 数据面服务地址（文件上传/取回走数据面）；留空＝由 apiUrl 的 <b>host</b> 推导
     * {@code http://{host}}（丢端口、走 80 path 路由），与 {@code CubeHttpClient.dataBaseUrl()}
     * 同口径；显式配置时整值覆盖（含端口原样使用）。
     */
    private String dataBaseUrl = "";

    /**
     * 建沙箱引用的镜像模板名
     */
    private String template;

    /**
     * 宿主机沙箱根目录（容器挂载源；会话工作目录为 hostRoot/<sessionId> 形态由棒2 定案）
     */
    private String hostRoot = "/data/sandbox";

    /**
     * 沙箱放行出网域名/网段白名单（空=不额外放行）。CubeVS 默认 deny 私网段，内网服务须显式放行；
     * 逗号分隔 CIDR/IP（单 IP 由平台补 /32）。如 {@code 10.0.0.0/8} 放行全部内网。
     */
    private List<String> allowOut = new ArrayList<>();

    /**
     * 沙箱出网代理开关：true＝建箱时容器级注入 HTTP(S)_PROXY（未命中 NO_PROXY 的目的地走代理），
     * false＝不注入代理 env。与 {@link #allowOut} 配合：allowOut 放行网段，代理决定出口路径。
     */
    private boolean proxyEnable = false;

    /** 出网代理主机（proxyEnable=true 时必填，如 10.3.4.230） */
    private String proxyHost = "";

    /** 出网代理端口（如 65535） */
    private int proxyPort = 0;

    /**
     * 免代理目的地（逗号分隔 host/IP/CIDR）→ 注入 NO_PROXY：命中的直连不走代理。
     * 如 MinIO {@code 10.3.5.112} 内网直连免代理，其余（MinerU/公网）走代理。
     */
    private String noProxy = "";

    /**
     * 沙箱保活 TTL（秒）：随 create body 的 {@code timeout} 字段下发（业务/管理沙箱同源，
     * 到期平台整箱回收）；管理面 HTTP 调用超时是客户端常量
     * （{@code CubeHttpClient.CALL_TIMEOUT}），与此无关。
     */
    private long adminTimeout = 86400;

    /**
     * 命令执行缺省超时（秒；exec 单次调用可覆盖）
     */
    private int timeout = 1800;

    /**
     * 沙箱所有权身份键：随 create body 的 {@code metadata.owner} 下发（业务/管理沙箱同源）；
     * 对账/清扫只回收 owner 等于本值的沙箱——平台共租户下防误杀他方沙箱的判据
     * （清扫/对账层消费）。多环境共用同一 CubeMaster 时建议各环境配不同 owner-id
     * （如 epc-ai-pre / epc-ai-dev）。
     */
    private String ownerId = "epc-ai";

    /**
     * 空闲回收阈值（秒）：会话沙箱最后活跃距今超过该值即判定空闲可回收（清扫循环消费）。
     */
    private long idleTimeout = 600;

    /**
     * 绝对寿命封顶（秒）：从创建起算的最大存活时长，到期无论活跃与否一律回收——防长会话
     * 僵尸沙箱无限占用宿主资源（清扫循环消费）。
     */
    private long maxLifetime = 21600;

    /**
     * 清扫循环间隔（秒）：孤儿/过期沙箱对账的轮询周期（清扫循环消费）。
     */
    private long cleanupInterval = 60;

    /**
     * 启动孤儿对账开关：进程启动时对账一遍平台沙箱列表，回收 owner 匹配但无会话归属的
     * 遗留沙箱（上次进程异常退出留下的孤儿）；关闭即跳过启动对账（清扫/对账层消费）。
     */
    private boolean orphanCleanupEnabled = true;
}
