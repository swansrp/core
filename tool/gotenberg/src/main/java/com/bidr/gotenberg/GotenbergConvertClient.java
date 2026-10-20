package com.bidr.gotenberg;

import com.bidr.document.DocumentConvertException;
import com.bidr.document.spi.ConvertStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Title: GotenbergConvertClient
 * Description: {@link ConvertStep} 的 gotenberg 实现——HTTP 直调 LibreOffice 转换通道
 * （形状自 InkHub {@code OfficeConvertClient} 下沉，口径一字不动：
 * 上传名固定 {@code source.<ext>}、按魔数自证 PDF、超时 5s/180s）。
 *
 * <p><b>失败语义两半</b>见 {@link DocumentConvertException}：转换层面的失败（4xx/未配置/产出
 * 不是 PDF）抛 {@code retryable=false} 让门面兜底（CONVERT_FALLBACK，原件直送引用降级无页）；
 * 服务不可达/5xx 抛 {@code retryable=true} 让调用方走重试预算——那是临时故障，静默兜底会把
 * 「暂时没页」固化成「永远没页」。实测备注（2026-09-20 原文带下）：LibreOffice 对损坏输入
 * 极宽容（按文本兜底导入也出 PDF），4xx 在真环境几乎不会发生。</p>
 *
 * @author Sharp
 * @since 2026/10/20
 */
@Slf4j
public class GotenbergConvertClient implements ConvertStep {

    /** 腿名（路线留痕用） */
    public static final String LEG_NAME = "gotenberg";

    /** LibreOffice 可转换、且转换后能产生页码的扩展名（大小写不敏感；本腿的认领面归本腿自报） */
    public static final Set<String> CONVERTIBLE_EXTS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("doc", "docx", "xls", "xlsx", "ppt", "pptx")));

    /** 连接超时：sidecar 在同网段，连不上说明部署问题，快速失败交重试 */
    private static final int CONNECT_TIMEOUT_MS = 5000;

    /** 读超时：LibreOffice 转大文档可能到分钟级（163KB docx 实测 1s），给足余量 */
    private static final int READ_TIMEOUT_MS = 180_000;

    private final GotenbergProperties properties;

    private final RestTemplate restTemplate;

    public GotenbergConvertClient(GotenbergProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        if (properties.getProxy().isEnable()) {
            factory.setProxy(new java.net.Proxy(java.net.Proxy.Type.HTTP, new java.net.InetSocketAddress(
                    properties.getProxy().getHost(), properties.getProxy().getPort())));
        }
        this.restTemplate = new RestTemplate(factory);
    }

    @Override
    public String legName() {
        return LEG_NAME;
    }

    @Override
    public boolean isConvertible(String filename) {
        if (filename == null) {
            return false;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return false;
        }
        String ext = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        return CONVERTIBLE_EXTS.contains(ext);
    }

    @Override
    public byte[] convertToPdf(String fileName, byte[] content) {
        String base = properties.getBaseUrl();
        if (!StringUtils.hasText(base)) {
            throw new DocumentConvertException(false,
                    "未配置 office 转换服务（tool.gotenberg.base-url）");
        }
        String ext = extOf(fileName);
        if (ext.isEmpty() || !CONVERTIBLE_EXTS.contains(ext)) {
            throw new DocumentConvertException(false, "不是可转换的 office 类型: " + fileName);
        }

        String url = base.replaceAll("/+$", "") + "/forms/libreoffice/convert";
        HttpHeaders headers = new HttpHeaders();
        // 坑 14 同族教训（原注释带下）：classpath 有 jackson-dataformat-xml 时内容协商不可依赖隐式行为，显式声明
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        // 上传名固定 source.<ext>：LibreOffice 只靠扩展名判断输入格式，固定 ASCII 名可规避
        // 中文文件名在 multipart Content-Disposition 里的编码歧义
        body.add("files", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return "source." + ext;
            }
        });

        try {
            ResponseEntity<byte[]> response =
                    restTemplate.postForEntity(url, new HttpEntity<>(body, headers), byte[].class);
            byte[] pdf = response.getBody();
            if (pdf == null || pdf.length == 0 || !looksLikePdf(pdf)) {
                throw new DocumentConvertException(false,
                        "转换服务返回内容不是 PDF: " + fileName + " bytes=" + (pdf == null ? 0 : pdf.length));
            }
            return pdf;
        } catch (HttpStatusCodeException e) {
            int code = e.getRawStatusCode();
            if (code >= 400 && code < 500 && code != 429) {
                // 文档本身转不动（格式损坏/特性不支持等）：重跑结果不变，走兜底
                throw new DocumentConvertException(false,
                        "office 转换被拒绝(HTTP " + code + "): " + fileName + " " + e.getResponseBodyAsString(), e);
            }
            throw new DocumentConvertException(true,
                    "office 转换服务错误(HTTP " + code + "): " + fileName, e);
        } catch (ResourceAccessException e) {
            throw new DocumentConvertException(true,
                    "office 转换服务不可达: " + base + " " + e.getMessage(), e);
        }
    }

    /** 按魔数判断 PDF（不看扩展名：对端返回什么就是什么） */
    private static boolean looksLikePdf(byte[] bytes) {
        return bytes.length > 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private static String extOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
