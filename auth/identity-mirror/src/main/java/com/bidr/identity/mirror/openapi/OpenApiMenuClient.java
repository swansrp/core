package com.bidr.identity.mirror.openapi;

import com.bidr.platform.openapi.OpenApiAuthProvider;
import com.bidr.platform.openapi.OpenApiDataClient;
import com.bidr.platform.openapi.OpenApiEnvelope;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Title: OpenApiMenuClient
 * Description: 子系统菜单树<b>场景客户端</b>（方案B 棒4 §2「core 菜单客户端」行）——
 * {@code GET {base}/open-api/v1/menu/tree?user={customerNumber}}（provider 侧 MenuTreeController
 * 的 mapping，机器凭证鉴权），解统一信封返回 {@link MenuNodeRes} 树。
 *
 * <p><b>三层分工</b>：传输/信封解包＝platform {@link OpenApiDataClient}（超时由构造方喂入，
 * 挂起防护第一道闸）；机器凭据＝{@link OpenApiAuthProvider} 注入面（宿主容器里由
 * authorization-client 的 {@code PartnerCredentialProvider} 提供实现——本模块只认接口，
 * <b>不依赖 authorization 任何类</b>，镜像纪律不破）；端点常量本类自持。</p>
 *
 * <p><b>装配形态</b>：纯类不标 {@code @Component}（构造参数的 {@code OpenApiAuthProvider}
 * bean 在宿主容器按 {@code my.oauth2.client.enabled} 条件装配，强扫会给未开开关的 mirror
 * 宿主强加装配约束）——消费方/宿主按需 {@code @Bean} 接线或直构：
 * {@code new OpenApiMenuClient(dataClient, authProvider)}。</p>
 *
 * <p><b>URL 编码</b>：customerNumber 走 UTF-8 URL 编码（工号形态虽多为字母数字，编码纪律
 * 统一——含中文/特殊字符工号不炸线）。</p>
 *
 * @author coder (棒4, 方案B 开放口三层沉淀 2026-11-07)
 * @since 2026/11/07
 */
public class OpenApiMenuClient {

    /** 菜单树端点固定路径（provider 侧 MenuTreeController 的 mapping） */
    public static final String TREE_PATH = "/open-api/v1/menu/tree";

    private static final ParameterizedTypeReference<OpenApiEnvelope<List<MenuNodeRes>>> MENU_TYPE =
            new ParameterizedTypeReference<OpenApiEnvelope<List<MenuNodeRes>>>() {
            };

    private final OpenApiDataClient dataClient;
    private final OpenApiAuthProvider authProvider;

    /** 构造注入：传输件＋机器凭据件（凭据实现归 authorization-client，本类只认接口） */
    public OpenApiMenuClient(OpenApiDataClient dataClient, OpenApiAuthProvider authProvider) {
        this.dataClient = dataClient;
        this.authProvider = authProvider;
    }

    /**
     * 拉取指定用户的子系统菜单树（解信封取 payload）。
     *
     * @param customerNumber 用户工号（provider 侧 user 同核判定的标识）
     * @return 菜单树（payload，非空——空树由信封空 payload 契约抛出）
     * @throws com.bidr.platform.openapi.OpenApiEnvelopeException 信封非成功码（携 code/msg）
     * @throws IllegalStateException                              信封 payload 为空／响应体为空／反序列化失败
     * @throws org.springframework.web.client.RestClientException HTTP 失败／超时／不可达（调用方兜）
     */
    public List<MenuNodeRes> fetchTree(String customerNumber) {
        String path = TREE_PATH + "?user=" + urlEncode(customerNumber);
        HttpHeaders headers = new HttpHeaders();
        authProvider.apply(headers);
        return dataClient.get(path, headers, MENU_TYPE);
    }

    /** UTF-8 URL 编码（customerNumber 入查询串纪律） */
    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
