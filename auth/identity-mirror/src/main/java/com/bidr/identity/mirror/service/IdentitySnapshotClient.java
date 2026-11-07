package com.bidr.identity.mirror.service;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.identity.mirror.IdentityMirrorProperties;
import com.bidr.identity.mirror.service.dto.SnapshotRes;
import com.bidr.kernel.utils.Base64Util;
import com.bidr.platform.openapi.OpenApiDataClient;
import com.bidr.platform.openapi.OpenApiEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Title: IdentitySnapshotClient
 * Description: 身份快照 HTTP 客户端（T2.3d 消费侧；方案B 棒4 起传输/信封下沉 platform）——
 * GET {base-url}/open-api/v1/identity/snapshot，头 {@code x-api-key: Base64(appKey:appSecret)}，
 * 与 provider 侧 {@code AuthPartner} 验签口径对称（Base64Util urlSafe 编码＋双解码兜底，
 * round-trip 同一口径）。
 *
 * <p><b>传输层（方案B 棒4 沉淀形态）</b>：HTTP 收发与信封解包归 platform
 * {@link OpenApiDataClient}（baseUrl 取本模块 {@link IdentityMirrorProperties}，超时值同源、
 * 由 productionRestTemplate 设入注入的 RestTemplate；信封 DTO 用 platform 最小
 * {@link OpenApiEnvelope}，宽容 Jackson——服务端加字段不炸消费侧）。
 * 本类只保留：配置完整性检查、机器凭证头注入、端点常量、{@code SnapshotRes} 负载类型面。</p>
 *
 * <p><b>响应信封</b>：hub 宿主对 com.bidr 包内控制器统一包 Response 信封
 * {@code {status:{code,msg,details}, payload:...}}（kernel 全局 ResponseExceptionHandler）——
 * {@code status.code==0} 取 payload 为快照；非 0 code 抛 {@code OpenApiEnvelopeException}
 * （IllegalStateException 子类，携 code/msg——保既有「非零信封抛 IllegalStateException 带
 * status.msg」契约，线格式契约测试断言不变）；空 payload 抛 {@link IllegalStateException}。
 * HTTP 层失败（非 2xx／超时／不可达）由 RestTemplate 抛 {@code RestClientException}——
 * 均由 {@link IdentityMirrorSyncJob} 兜住记日志，不炸调度线程。</p>
 *
 * <p><b>超时</b>：生产构造走 {@link SimpleClientHttpRequestFactory} 显式 connect/read 超时
 * （identity.mirror.sync.connect-timeout-ms / read-timeout-ms，缺省 3000/5000，值喂给
 * OpenApiDataClient 构造）。hub 挂起防护：本模块无 failMode 可言（调度线程饿死连失败信号
 * 都没有），<b>超时是第一道闸</b>。</p>
 *
 * <p><b>测试缝</b>：{@link RestTemplate} 经包内可见构造注入可替换（线格式契约测试
 * {@code IdentitySnapshotClientEnvelopeContractTest} 用 MockRestServiceServer 绑定注入的
 * RestTemplate、走真 Jackson 反序列化链——形态保留，断言未改）。</p>
 *
 * <p>🔴 mirror 纪律：本模块禁依赖 authorization-client——传输面取 <b>platform</b> 的
 * OpenApiDataClient（方案B 棒4 起，platform 零 auth 依赖的最底层）；凭据头本类自持
 * （RequestConst.X_API_KEY 常量在 authorization-api，constants 白名单内）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Component
public class IdentitySnapshotClient {

    /** 快照端点固定路径（provider 侧 IdentitySnapshotController 的 mapping） */
    public static final String SNAPSHOT_PATH = "/open-api/v1/identity/snapshot";

    private static final ParameterizedTypeReference<OpenApiEnvelope<SnapshotRes>> ENVELOPE_TYPE =
            new ParameterizedTypeReference<OpenApiEnvelope<SnapshotRes>>() {
            };

    private final IdentityMirrorProperties properties;
    private final OpenApiDataClient dataClient;

    /**
     * 生产构造：SimpleClientHttpRequestFactory 显式 connect/read 超时（挂起防护，见类 javadoc），
     * 带 properties 超时的 RestTemplate 经 {@link OpenApiDataClient} 测试缝构造注入（超时单一出处
     * ＝productionRestTemplate）。
     * 🔴 必须标 {@code @Autowired}：本类是 {@code @Component} 且存在第二个包内测试缝构造——
     * Spring 5.3 构造器决议对「多构造＋无注解＋无默认构造」返回 null（装配失败），双构造形态下
     * 未标注的候选构造不会被选用（review 第 2 轮 N-C-1 实证）。
     */
    @Autowired
    public IdentitySnapshotClient(IdentityMirrorProperties properties) {
        this(properties, productionRestTemplate(properties));
    }

    /** 测试缝构造（包内可见）：RestTemplate 可注入替换（契约测试 MockRestServiceServer 绑定形态；
     *  生产路径的 RestTemplate 已带 properties 超时——productionRestTemplate 单一出处） */
    IdentitySnapshotClient(IdentityMirrorProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.dataClient = new OpenApiDataClient(properties.getBaseUrl(), restTemplate);
    }

    /** 生产 RestTemplate：直连构造（任务裁定：勿引重件、不经容器装配）＋显式超时 */
    static RestTemplate productionRestTemplate(IdentityMirrorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return new RestTemplate(factory);
    }

    /**
     * 拉取一份全量身份快照（解信封取 payload）。
     *
     * @return 快照（watermark＋三段全量）
     * @throws IllegalStateException base-url/app-key/app-secret 未配置齐（误开形态，调用方兜住）；
     *                               或响应非成功信封（status.code!=0，带 status.msg）／payload 为空
     * @throws org.springframework.web.client.RestClientException HTTP 失败／超时／不可达
     */
    public SnapshotRes fetchSnapshot() {
        if (!StringUtils.hasText(properties.getBaseUrl())
                || !StringUtils.hasText(properties.getAppKey())
                || !StringUtils.hasText(properties.getAppSecret())) {
            throw new IllegalStateException(
                    "identity.mirror.sync 配置不完整（base-url/app-key/app-secret 均必填），快照拉取拒绝执行");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestConst.X_API_KEY, Base64Util.encode(properties.getAppKey() + ":" + properties.getAppSecret()));
        return dataClient.get(SNAPSHOT_PATH, headers, ENVELOPE_TYPE);
    }
}
