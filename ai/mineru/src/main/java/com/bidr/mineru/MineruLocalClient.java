package com.bidr.mineru;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * Title: MineruLocalClient
 * Description: MinerU <b>自建</b>腿客户端（mineru-api 3.x 契约，异步任务式）：
 * {@code POST /tasks} multipart 提交 → {@code GET /tasks/{id}} 轮询 → {@code GET /tasks/{id}/result} 取 zip。
 * 协议归一自参照实现（InkHub 引擎侧/pm-hse），实测约束全带：
 * <ul>
 *   <li>multipart 字段名 <b>files</b>（复数）；</li>
 *   <li>{@code return_md} / {@code return_content_list} / {@code response_format_zip} 必须显式开
 *       （默认均 false，拿不到结果）；</li>
 *   <li>轮询 status 词表＝pending/completed/failed，failed 的原因在 error 字段，
 *       backend 字段作 phase、queued_ahead 供并发闸门。</li>
 * </ul>
 * 供同步门面 {@link MineruTranscriber} 与后台任务链式消费方共用（后者自己管轮询节奏）。
 *
 * @author Sharp
 * @since 2026/10/17
 */
public class MineruLocalClient {

    private final MineruProperties properties;

    private final RestTemplate restTemplate;

    public MineruLocalClient(MineruProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        Proxy proxy = buildProxy(properties.getProxy());
        if (proxy != null) {
            factory.setProxy(proxy);
        }
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * 提交解析任务。
     *
     * @param content       文件字节
     * @param filename      原始文件名（含扩展名，MinerU 据此识别类型）
     * @param lang          语言（空则用 properties.lang）
     * @param extractImages 是否要图片条目（false＝return_images 关，结果 zip 更小；纯转写场景建议 false）
     * @return taskId
     */
    public String submit(byte[] content, String filename, String lang, boolean extractImages) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("MinerU 解析内容为空: " + filename);
        }
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("files", namedBytes(content, filename));
        form.add("lang_list", StringUtils.hasText(lang) ? lang : properties.getLang());
        form.add("backend", StringUtils.hasText(properties.getLocal().getBackend())
                ? properties.getLocal().getBackend() : "hybrid-auto-engine");
        form.add("parse_method", "auto");
        form.add("return_md", "true");
        form.add("return_content_list", "true");
        form.add("return_images", String.valueOf(extractImages));
        form.add("response_format_zip", "true");

        JsonNode resp = restTemplate.postForObject(url("/tasks"), form, JsonNode.class);
        if (resp == null || !resp.hasNonNull("task_id")) {
            throw new IllegalStateException("MinerU 提交失败: " + resp);
        }
        return resp.get("task_id").asText();
    }

    /** 轮询任务状态（归一四态） */
    public MineruTaskStatus poll(String taskId) {
        JsonNode resp = restTemplate.getForObject(url("/tasks/" + taskId), JsonNode.class);
        MineruTaskStatus status = new MineruTaskStatus();
        if (resp == null) {
            status.setStatus(MineruTaskStatus.RUNNING);
            return status;
        }
        String raw = resp.path("status").asText("");
        if ("completed".equals(raw)) {
            status.setStatus(MineruTaskStatus.COMPLETED);
        } else if ("failed".equals(raw)) {
            status.setStatus(MineruTaskStatus.FAILED);
            status.setErrorMessage(resp.path("error").asText(""));
        } else if ("pending".equals(raw)) {
            status.setStatus(MineruTaskStatus.PENDING);
        } else {
            status.setStatus(MineruTaskStatus.RUNNING);
        }
        status.setPhase(resp.path("backend").asText(null));
        if (resp.hasNonNull("queued_ahead")) {
            status.setQueueAhead(resp.get("queued_ahead").asInt());
        }
        return status;
    }

    /** 取结果 zip 并归一（markdown/content_list/图片） */
    public MineruArtifact fetch(String taskId) {
        byte[] zip = restTemplate.getForObject(url("/tasks/" + taskId + "/result"), byte[].class);
        return MineruArtifactCodec.readZip(zip, taskId);
    }

    /** multipart 文件部件：带 filename 的 ByteArrayResource（MinerU 据扩展名识别文档类型） */
    private static ByteArrayResource namedBytes(byte[] content, String fileName) {
        return new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }

    private String url(String path) {
        String base = properties.getLocal().getBaseUrl();
        if (base != null && base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + path;
    }

    static Proxy buildProxy(MineruProperties.Proxy config) {
        if (config == null || !config.isEnable() || !StringUtils.hasText(config.getHost()) || config.getPort() <= 0) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getHost(), config.getPort()));
    }
}
