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
     * 沙箱放行出网域名/网段白名单（空=不额外放行）
     */
    private List<String> allowOut = new ArrayList<>();

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
}
