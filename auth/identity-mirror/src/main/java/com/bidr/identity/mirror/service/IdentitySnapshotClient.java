package com.bidr.identity.mirror.service;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.identity.mirror.IdentityMirrorProperties;
import com.bidr.identity.mirror.service.dto.SnapshotRes;
import com.bidr.kernel.utils.Base64Util;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Title: IdentitySnapshotClient
 * Description: 身份快照 HTTP 客户端（T2.3d 消费侧）——GET {base-url}/open-api/v1/identity/snapshot，
 * 头 {@code x-api-key: Base64(appKey:appSecret)}，与 provider 侧 {@code AuthPartner} 验签口径
 * 对称（Base64Util urlSafe 编码＋双解码兜底，round-trip 同一口径）。
 *
 * <p><b>响应信封</b>：hub 宿主对 com.bidr 包内控制器统一包 Response 信封
 * {@code {status:{code,msg,details}, payload:...}}（kernel 全局 ResponseExceptionHandler，
 * {@code @ControllerAdvice + ResponseBodyAdvice}——snapshot 直返 DTO 亦被包裹），本类自带同构
 * DTO 解包：{@code status.code==0} 取 payload 为快照；非 0 code／空 payload 抛
 * {@link IllegalStateException}（带 status.msg——鉴权失败等业务错误从「静默空快照」变显式异常）。
 * HTTP 层失败（非 2xx／超时／不可达）由 RestTemplate 抛 {@code RestClientException}——
 * 均由 {@link IdentityMirrorSyncJob} 兜住记日志，不炸调度线程。</p>
 *
 * <p><b>超时</b>：生产构造走 {@link SimpleClientHttpRequestFactory} 显式 connect/read 超时
 * （identity.mirror.sync.connect-timeout-ms / read-timeout-ms，缺省 3000/5000）。hub 挂起防护：
 * 本模块无 failMode 可言（调度线程饿死连失败信号都没有），<b>超时是第一道闸</b>。</p>
 *
 * <p><b>测试缝</b>：{@link RestTemplate} 经包内可见构造注入可替换（线格式契约测试
 * {@code IdentitySnapshotClientEnvelopeContractTest} 用 MockRestServiceServer 走真 Jackson 反序列化）。</p>
 *
 * <p>🔴 mirror 纪律：本模块禁依赖 authorization-client——信封 DTO 本地自带（与 client 侧
 * HttpAuthzCheckApi 的 Envelope 同构但互不引用，两模块互不依赖）。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Component
public class IdentitySnapshotClient {

    /** 快照端点固定路径（provider 侧 IdentitySnapshotController 的 mapping） */
    public static final String SNAPSHOT_PATH = "/open-api/v1/identity/snapshot";

    /** kernel Response 信封的成功码（ErrCodeSys.SUCCESS=0） */
    static final int ENVELOPE_SUCCESS_CODE = 0;

    private static final ParameterizedTypeReference<Envelope<SnapshotRes>> ENVELOPE_TYPE =
            new ParameterizedTypeReference<Envelope<SnapshotRes>>() {
            };

    private final IdentityMirrorProperties properties;
    private final RestTemplate restTemplate;

    /**
     * 生产构造：SimpleClientHttpRequestFactory 显式 connect/read 超时（挂起防护，见类 javadoc）。
     * 🔴 必须标 {@code @Autowired}：本类是 {@code @Component} 且存在第二个包内测试缝构造——
     * Spring 5.3 构造器决议对「多构造＋无注解＋无默认构造」返回 null（装配失败），双构造形态下
     * 未标注的候选构造不会被选用（review 第 2 轮 N-C-1 实证）。
     */
    @Autowired
    public IdentitySnapshotClient(IdentityMirrorProperties properties) {
        this(properties, productionRestTemplate(properties));
    }

    /** 测试缝构造（包内可见）：RestTemplate 可注入替换 */
    IdentitySnapshotClient(IdentityMirrorProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
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
        String url = properties.getBaseUrl() + SNAPSHOT_PATH;
        HttpHeaders headers = new HttpHeaders();
        headers.set(RequestConst.X_API_KEY, Base64Util.encode(properties.getAppKey() + ":" + properties.getAppSecret()));
        ResponseEntity<Envelope<SnapshotRes>> response =
                restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), ENVELOPE_TYPE);
        Envelope<SnapshotRes> envelope = response.getBody();
        if (envelope == null) {
            throw new IllegalStateException("快照响应体为空: " + url);
        }
        envelope.assertSuccess();
        return envelope.getPayload();
    }

    // ---------------- 响应信封 DTO（kernel Response 同构，mirror 侧本地自带） ----------------

    /** {@code {status:{code,msg,details}, payload:T}} 信封（mirror 本地，勿引 authorization-client 同名类） */
    @Data
    static class Envelope<T> {

        private Status status;
        private T payload;

        void assertSuccess() {
            boolean ok = status != null && status.getCode() != null
                    && ENVELOPE_SUCCESS_CODE == status.getCode();
            if (!ok) {
                String detail = status == null ? "无 status"
                        : (status.getCode() == null ? "无 code" : status.getCode() + "/" + status.getMsg());
                throw new IllegalStateException("身份快照端点返回非成功信封: " + detail);
            }
            if (payload == null) {
                throw new IllegalStateException("身份快照信封 payload 为空");
            }
        }
    }

    @Data
    static class Status {
        private Integer code;
        private String msg;
        private String details;
    }
}
