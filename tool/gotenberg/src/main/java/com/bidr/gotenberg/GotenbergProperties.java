package com.bidr.gotenberg;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: GotenbergProperties
 * Description: gotenberg 转换服务配置（{@code tool.gotenberg.*}，纯 yml＋Spring Environment，
 * 零 sys_config，R-9）。🔴 <b>没有 provider 选型键</b>（D10 定形：convert 槽只有这一条腿，
 * 一把 {@code base-url} 就够）——留空＝不装配＝office 原件直送、引用降级无页
 * （RouteTrace 记 CONVERT_FALLBACK 形状）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Data
@ConfigurationProperties(prefix = "tool.gotenberg")
public class GotenbergProperties {

    /**
     * gotenberg 服务地址（如 http://127.0.0.1:13000）。留空＝不装配：office 文件按原件解析
     * （引用降级无页）。
     */
    private String baseUrl = "";

    /**
     * HTTP 代理：gotenberg 服务须经代理访问时启用（与 {@code llm.proxy.*}、
     * {@code spring.elasticsearch.proxy.*} 同口径的环境绑定值，改了要重启）；
     * enable=false（默认）＝直连，零行为变化。
     */
    private final Proxy proxy = new Proxy();

    @Data
    public static class Proxy {

        /** 是否启用代理（默认直连） */
        private boolean enable = false;

        /** 代理主机（enable=true 时必填） */
        private String host = "";

        /** 代理端口 */
        private int port = 0;
    }
}
