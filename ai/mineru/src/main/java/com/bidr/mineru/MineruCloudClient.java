package com.bidr.mineru;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

/**
 * Title: MineruCloudClient
 * Description: MinerU <b>公网</b>（mineru.net）腿客户端，v4 契约。与自建腿是两套协议
 * （不接受 multipart 提交），三点实测差异全带（2026-10-10 逐腿实测归一）：
 * <ol>
 *   <li><b>提交＝两段式</b>：{@code POST /api/v4/file-urls/batch}（Bearer）换 {@code data.batch_id}
 *       ＋预签名 {@code data.file_urls[0]}，再把原件字节 PUT 上去。🔴 直传必须<b>一个 Content-Type
 *       都不发</b>：预签名按空 content-type 计算，实测任何头都会回 OSS 403 SignatureDoesNotMatch
 *       ——所以走 RequestCallback 直写字节、绕开消息转换器加默认头那条路；</li>
 *   <li><b>轮询＝按批次查</b>：{@code GET /api/v4/extract-results/batch/{batch_id}} 取
 *       {@code data.extract_result[0]}（state／err_msg／full_zip_url）；状态词表比自建多
 *       waiting-file／converting，归一进四态；</li>
 *   <li><b>结果＝zip 直链</b>：full_zip_url 在 CDN 上免鉴权；🔴 地址必须用 {@link URI} 传给
 *       RestTemplate——字符串重载会把地址当 URI 模板展开，% 与 {} 会被改写。</li>
 * </ol>
 * 业务失败＝HTTP 200＋非 0 {@code code}（例：令牌过期 A0211、额度用尽），是<b>终局</b>：
 * {@link #poll} 判 FAILED 不再等，把任务吊在轮询里只会浪费窗口。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class MineruCloudClient {

    private static final String API = "/api/v4";

    private final MineruProperties properties;

    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public MineruCloudClient(MineruProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        java.net.Proxy proxy = MineruLocalClient.buildProxy(properties.getProxy());
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 两段式提交：申请直传地址 → PUT 原件字节。
     *
     * @param content  文件字节
     * @param filename 原始文件名（含扩展名）
     * @param lang     语言（空则用 cloud.language）
     * @return batchId
     */
    public String submit(byte[] content, String filename, String lang) {
        requireToken();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("enable_formula", properties.getCloud().isEnableFormula());
        body.put("enable_table", properties.getCloud().isEnableTable());
        body.put("model_version", properties.getCloud().getModelVersion());
        body.put("language", StringUtils.hasText(lang) ? lang : properties.getCloud().getLanguage());
        ArrayNode files = body.putArray("files");
        ObjectNode file = files.addObject();
        file.put("name", filename);
        file.put("is_ocr", !Boolean.FALSE.equals(properties.getCloud().getIsOcr()));

        JsonNode resp = call(HttpMethod.POST, api("/file-urls/batch"), body, "申请直传地址");
        String batchId = resp.path("data").path("batch_id").asText("");
        String uploadUrl = resp.path("data").path("file_urls").path(0).asText("");
        if (!StringUtils.hasText(batchId) || !StringUtils.hasText(uploadUrl)) {
            throw new IllegalStateException("MinerU 云端直传地址响应不完整: " + resp);
        }
        upload(content, uploadUrl);
        return batchId;
    }

    /** 轮询批次状态（归一四态）；非 0 业务码＝终局 FAILED */
    public MineruTaskStatus poll(String batchId) {
        MineruTaskStatus status = new MineruTaskStatus();
        JsonNode resp;
        try {
            resp = call(HttpMethod.GET, api("/extract-results/batch/" + batchId), null, "查询解析状态");
        } catch (IllegalStateException e) {
            status.setStatus(MineruTaskStatus.FAILED);
            status.setErrorMessage(e.getMessage());
            return status;
        }
        JsonNode result = resp.path("data").path("extract_result").path(0);
        String state = result.path("state").asText("");
        status.setPhase(state);
        if ("done".equals(state)) {
            status.setStatus(MineruTaskStatus.COMPLETED);
        } else if ("failed".equals(state)) {
            status.setStatus(MineruTaskStatus.FAILED);
            status.setErrorMessage(result.path("err_msg").asText(""));
        } else if ("pending".equals(state) || "waiting-file".equals(state)) {
            status.setStatus(MineruTaskStatus.PENDING);
        } else {
            // running／converting，以及公网侧新增的任何中间态：按在跑处理
            status.setStatus(MineruTaskStatus.RUNNING);
        }
        return status;
    }

    /** 取结果 zip 直链并归一 */
    public MineruArtifact fetch(String batchId) {
        JsonNode resp = call(HttpMethod.GET, api("/extract-results/batch/" + batchId), null, "取结果地址");
        String zipUrl = resp.path("data").path("extract_result").path(0).path("full_zip_url").asText("");
        if (!StringUtils.hasText(zipUrl)) {
            throw new IllegalStateException("MinerU 云端结果 zip 地址为空: " + batchId);
        }
        byte[] zip = restTemplate.getForObject(URI.create(zipUrl), byte[].class);
        return MineruArtifactCodec.readZip(zip, batchId);
    }

    /**
     * 直传原件字节。<b>刻意不发 Content-Type</b>（见类注释第 1 条）：走
     * {@link RestTemplate#execute}＋RequestCallback 自己写流，绕开消息转换器加默认头那条路。
     */
    private void upload(byte[] content, String uploadUrl) {
        try {
            restTemplate.execute(URI.create(uploadUrl), HttpMethod.PUT,
                    request -> request.getBody().write(content), null);
        } catch (Exception e) {
            throw new IllegalStateException("MinerU 云端原件直传失败: " + e.getMessage(), e);
        }
    }

    private JsonNode call(HttpMethod method, String fullUrl, JsonNode body, String action) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getCloud().getToken());
        if (body != null) {
            // 实测：申请直传地址必须显式 application/json。同体发 text/plain 时公网回
            // HTTP 200＋code=-10002「type mismatch for field "files"」＝按另一种内容类型解析了请求体
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        HttpEntity<String> entity = new HttpEntity<>(body == null ? null : body.toString(), headers);
        ResponseEntity<JsonNode> exchange;
        try {
            // URI 而不是字符串：地址里没有模板变量，不能让 RestTemplate 做二次编码
            exchange = restTemplate.exchange(URI.create(fullUrl), method, entity, JsonNode.class);
        } catch (Exception e) {
            throw new IllegalStateException("MinerU 云端" + action + "请求失败: " + e.getMessage(), e);
        }
        JsonNode resp = exchange.getBody();
        int code = resp == null ? -1 : resp.path("code").asInt(-1);
        if (code != 0) {
            throw new IllegalStateException("MinerU 云端" + action + "失败: code=" + code
                    + " msg=" + (resp == null ? "无响应体" : resp.path("msg").asText("")));
        }
        return resp;
    }

    private void requireToken() {
        if (!StringUtils.hasText(properties.getCloud().getToken())) {
            throw new IllegalStateException("未配置 MinerU 云端令牌（mineru.cloud.token）");
        }
    }

    private String api(String path) {
        return apiBase() + API + path;
    }

    /** 基址去结尾斜杠；公网默认 https://mineru.net */
    private String apiBase() {
        String base = StringUtils.hasText(properties.getCloud().getBaseUrl())
                ? properties.getCloud().getBaseUrl() : "https://mineru.net";
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
