package com.bidr.parse;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Title: ParseEngineProperties
 * Description: 解析编排引擎配置（{@code engine.parse.*}，纯 yml＋Spring Environment，
 * 🔴 零 sys_config、不依赖 platform 缓存）。全部键带缺省＝不配即可跑；等待预算缺省值与
 * 业务流水线历史写死值相同（3600 档，与同步门面 300 档两档分立——判据⑧）。
 *
 * @author Sharp
 * @since 2026-10-21
 */
@Data
@ConfigurationProperties(prefix = "engine.parse")
public class ParseEngineProperties {

    /**
     * 异步转录腿选型键（纯 yml，改值要重启）：值＝腿名（如 {@code mineru}）。
     * 空＝取 classpath 上第一条在装异步腿（无腿＝内置直读，不抛）；
     * 配 {@code builtin}／{@code none}＝强制内置；配了但腿不在 classpath＝WARN 后走内置。
     */
    private String transcriber = "";

    /**
     * 外部解析引擎等待预算（毫秒，缺省 3600 档）：超预算＝可重试失败、重提交新单。
     * 与同步门面 {@code tool.document.*} 的 300 档两档分立（判据⑧）。
     */
    private long mineruMaxWaitMs = 3_600_000L;

    /**
     * 直通腿外链图片主机白名单（逗号分隔，精确匹配主机名，大小写不敏感）。
     * 缺省空＝不拉任何外链；要放行内网图床就在这里加主机（改了要重启，属环境绑定值）。
     */
    private String passthroughRemoteImageHosts = "";
}
