package com.bidr.document.builtin;

import com.bidr.document.spi.AgentHealthContributor;
import com.bidr.mineru.MineruLocalClient;
import com.bidr.mineru.MineruProperties;

/**
 * Title: MineruHealthContributor
 * Description: MinerU 本地转写服务的<b>健康自报</b>（AgentHealthContributor 腿实现，2026-11-18
 * 探活架构改造——各腿自报、中央只收集，用户拍板「每个子模块自己探自己的」）：探测口径与
 * {@link MineruLocalClient} 同源内聚——{@code GET {mineru.local.base-url}/docs}（FastAPI 文档页
 * 有无不保证，<b>拿到任何 HTTP 状态码（含 404）＝服务在＝可达</b>）；代理同步走
 * {@link MineruLocalClient#buildProxy} 同式（外网段底座直连探测恒 timeout 误报的教训，
 * 2026-11-14 pre 实锤）。{@code mineru.local.base-url} 空＝本地腿没接线＝NOT_CONFIGURED
 * （部署形态非故障——cloud 腿用户不经过本地服务）。bean 注册在 DocumentAutoConfiguration
 * 的 {@code MineruLegConfiguration}（与 {@link MineruTranscribeLeg} 同款条件）——腿在探活才在。
 *
 * @author Sharp
 * @since 2026/11/18
 */
public class MineruHealthContributor implements AgentHealthContributor {

    /** 能力位名（快照键，与 llm AgentFileCapabilityResolver.OCR 同字面） */
    static final String CAPABILITY_OCR = "ocr";

    private final MineruProperties properties;

    public MineruHealthContributor(MineruProperties properties) {
        this.properties = properties;
    }

    @Override
    public String capability() {
        return CAPABILITY_OCR;
    }

    @Override
    public String probeName() {
        return "mineru";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public HealthReport probe() {
        String baseUrl = properties.getLocal() != null ? properties.getLocal().getBaseUrl() : "";
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return HealthReport.notConfigured();
        }
        String target = baseUrl.trim();
        if (target.endsWith("/")) {
            target = target.substring(0, target.length() - 1);
        }
        java.net.Proxy proxy = MineruLocalClient.buildProxy(properties.getProxy());
        // mineru 判定＝任意 HTTP 状态码（含 404）＝可达——谓词恒真，拿到状态码就证明「服务在」
        return AgentHealthContributor.httpGet(target + "/docs", proxy, code -> true);
    }
}
