package com.bidr.identity.mirror.service;

import com.bidr.authorization.constants.common.RequestConst;
import com.bidr.identity.mirror.IdentityMirrorProperties;
import com.bidr.identity.mirror.service.dto.SnapshotRes;
import com.bidr.kernel.utils.Base64Util;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * Title: IdentitySnapshotClient
 * Description: 身份快照 HTTP 客户端（T2.3d 消费侧）——GET {base-url}/open-api/v1/identity/snapshot，
 * 头 {@code x-api-key: Base64(appKey:appSecret)}，与 provider 侧 {@code AuthPartner} 验签口径
 * 对称（Base64Util urlSafe 编码＋双解码兜底，round-trip 同一口径）。
 * <p>HTTP 通道＝RestTemplate 直连构造（spring-web 已在依赖内，不引重件；镜像同步低频全量拉取，
 * 无连接池诉求）。鉴权失败/非 2xx 由 RestTemplate 抛 {@code HttpClientErrorException} 等——
 * 由 {@link IdentityMirrorSyncJob} 兜住记日志，不炸调度线程。</p>
 *
 * @author Sharp
 * @since 2026/11/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentitySnapshotClient {

    /** 快照端点固定路径（provider 侧 IdentitySnapshotController 的 mapping） */
    public static final String SNAPSHOT_PATH = "/open-api/v1/identity/snapshot";

    private final IdentityMirrorProperties properties;

    /** 直连构造（任务裁定：勿引重件、不经容器装配） */
    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 拉取一份全量身份快照。
     *
     * @return 快照（watermark＋三段全量）
     * @throws IllegalStateException base-url/app-key/app-secret 未配置齐（误开形态，调用方兜住）
     * @throws org.springframework.web.client.RestClientException HTTP 失败／鉴权不过
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
        ResponseEntity<SnapshotRes> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), SnapshotRes.class);
        SnapshotRes snapshot = response.getBody();
        if (snapshot == null) {
            throw new IllegalStateException("快照响应体为空: " + url);
        }
        return snapshot;
    }
}
