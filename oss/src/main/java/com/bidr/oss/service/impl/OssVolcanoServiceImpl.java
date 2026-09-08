package com.bidr.oss.service.impl;

import com.bidr.oss.constant.OssConst;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.PartInfo;
import com.bidr.oss.vo.UploadRes;
import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.model.object.AbortMultipartUploadInput;
import com.volcengine.tos.model.object.CompleteMultipartUploadV2Input;
import com.volcengine.tos.model.object.CreateMultipartUploadInput;
import com.volcengine.tos.model.object.DeleteObjectInput;
import com.volcengine.tos.model.object.ListPartsInput;
import com.volcengine.tos.model.object.ListPartsOutput;
import com.volcengine.tos.model.object.ObjectMetaRequestOptions;
import com.volcengine.tos.model.object.PreSignedURLInput;
import com.volcengine.tos.model.object.PutObjectInput;
import com.volcengine.tos.model.object.UploadPartV2Input;
import com.volcengine.tos.model.object.UploadedPartV2;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Title: OssVolcanoServiceImpl
 * Description: 火山引擎对象存储（TOS）实现，使用火山官方 TOS SDK。
 * 注意：TOS 不支持 S3 path-style 访问（minio 客户端请求会报 InvalidPathAccess），
 * oss.endpoint 需配置为原生端点（如 https://tos-cn-shanghai.volces.com），region 自动从中解析
 * Copyright: Copyright (c) 2026 Company: plsintec Ltd.
 *
 * @author sharp
 * @since 2026/09/04 01:00
 */
@Slf4j
@Service
public class OssVolcanoServiceImpl extends BaseOssService {

    /** 预签名有效期：7 天（TOS 上限，用于上传响应地址） */
    private static final int PRESIGN_EXPIRE_SECONDS = 7 * 24 * 3600;
    /** 读取预签名有效期：1 小时（每次访问实时签发，避免长期签名过期/泄露风险） */
    private static final int READ_EXPIRE_SECONDS = 3600;
    private static final Pattern REGION_PATTERN = Pattern.compile("tos-([a-z0-9-]+)\\.volces\\.com");

    /** TOS 客户端持有连接池，懒加载单例复用 */
    private volatile TOSV2 tosClient;

    private TOSV2 client() {
        if (tosClient == null) {
            synchronized (this) {
                if (tosClient == null) {
                    // build(region, endpoint, ak, sk)：TOS 仅支持虚拟主机式访问，SDK 自动拼 bucket.endpoint
                    tosClient = new TOSV2ClientBuilder()
                            .build(resolveRegion(endpoint), endpoint, appKey, appSecret);
                }
            }
        }
        return tosClient;
    }

    /** 从端点解析地域：https://tos-cn-shanghai.volces.com -> cn-shanghai */
    private static String resolveRegion(String endpoint) {
        Matcher matcher = REGION_PATTERN.matcher(endpoint);
        return matcher.find() ? matcher.group(1) : "cn-beijing";
    }

    @Override
    public String buildAccessUrl(String objectName) {
        return endpoint + OssConst.SEP + bucketName + OssConst.SEP + objectName;
    }

    @Override
    @SneakyThrows
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String objectName = buildObjectName(request, file, folder, type, fileName);
        // 桶由控制台预先创建（AK 通常无桶管理权限），直接上传；
        // Content-Type 必须显式设置（SDK 经 ObjectMetaRequestOptions 元数据设置）：
        // TOS 默认 octet-stream 会让浏览器（含预览 iframe）只能下载
        client().putObject(new PutObjectInput().setBucket(bucketName).setKey(objectName)
                .setOptions(new ObjectMetaRequestOptions()
                        .setContentType(resolveContentType(file.getContentType(), objectName)))
                .setContent(file.getInputStream()));
        String url = buildAccessUrl(objectName);
        SaObjectStorage oss = record(objectName, type, url, file.getSize());
        UploadRes uploadRes = buildUploadVO(oss);
        uploadRes.setUrl(presign(oss.getKey()));
        return uploadRes;
    }

    @Override
    public void delete(Long id) {

    }

    @Override
    @SneakyThrows
    public void deleteObject(String url) {
        String key = getKey(url);
        log.info("deleteObject == {}", key);
        client().deleteObject(new DeleteObjectInput().setBucket(bucketName).setKey(key));
    }

    @Override
    public String getReadUrl(String url) {
        return getReadUrl(url, null);
    }

    @Override
    public String getReadUrl(String url, String fileName) {
        String key = getKey(url);
        log.info("getReadUrl == {}", key);
        return presign(key, READ_EXPIRE_SECONDS, fileName);
    }

    /**
     * 预览地址：签名显式覆盖 response-content-type（按扩展名推断）与
     * response-content-disposition=inline，存量对象即使元数据为 octet-stream
     * 也能内联展示（而非触发下载），且即使下载保存名也是原文件名
     */
    @Override
    public String getPreviewUrl(String url, String fileName) {
        String key = getKey(url);
        log.info("getPreviewUrl == {}", key);
        PreSignedURLInput input = new PreSignedURLInput()
                .setBucket(bucketName)
                .setKey(key)
                .setHttpMethod("GET")
                .setExpires(READ_EXPIRE_SECONDS);
        Map<String, String> query = new java.util.HashMap<>();
        query.put("response-content-disposition", inlineDisposition(fileName));
        String mime = contentTypeOf(key);
        if (mime != null) {
            query.put("response-content-type", mime);
        }
        input.setQuery(query);
        return client().preSignedURL(input).getSignedUrl();
    }

    /** 上传响应地址：7 天有效期 */
    private String presign(String objectName) {
        return presign(objectName, PRESIGN_EXPIRE_SECONDS, null);
    }

    /** 生成 GET 预签名地址（私有桶可匿名访问，有效期由调用方指定；带文件名时浏览器下载保存名与原文件名一致） */
    private String presign(String objectName, int expiresSeconds, String downloadName) {
        PreSignedURLInput input = new PreSignedURLInput()
                .setBucket(bucketName)
                .setKey(objectName)
                .setHttpMethod("GET")
                .setExpires(expiresSeconds);
        if (downloadName != null && !downloadName.isEmpty()) {
            input.setQuery(Collections.singletonMap("response-content-disposition", contentDisposition(downloadName)));
        }
        return client().preSignedURL(input).getSignedUrl();
    }

    // ===================== 分片上传（断点续传，TOS V2 接口） =====================

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    @SneakyThrows
    public String initMultipartUpload(String objectName) {
        // 分片对象同样显式设置 Content-Type（SDK 经 ObjectMetaRequestOptions，扩展名在 objectName 中保留），
        // 避免合并后元数据为 octet-stream
        return client().createMultipartUpload(new CreateMultipartUploadInput()
                .setBucket(bucketName).setKey(objectName)
                .setOptions(new ObjectMetaRequestOptions()
                        .setContentType(resolveContentType(null, objectName)))).getUploadID();
    }

    @Override
    @SneakyThrows
    public String uploadPart(String objectName, String uploadId, int partNumber, InputStream in, long partSize) {
        return client().uploadPart(new UploadPartV2Input()
                .setBucket(bucketName).setKey(objectName).setUploadID(uploadId)
                .setPartNumber(partNumber).setContent(in).setContentLength(partSize)).getEtag();
    }

    @Override
    @SneakyThrows
    public List<PartInfo> listUploadedParts(String objectName, String uploadId) {
        List<PartInfo> res = new ArrayList<>();
        int marker = 0;
        for (;;) {
            ListPartsOutput out = client().listParts(new ListPartsInput()
                    .setBucket(bucketName).setKey(objectName).setUploadID(uploadId)
                    .setPartNumberMarker(marker).setMaxParts(1000));
            if (out.getUploadedParts() != null) {
                for (UploadedPartV2 part : out.getUploadedParts()) {
                    res.add(new PartInfo(part.getPartNumber(), part.getEtag()));
                }
            }
            if (!out.isTruncated()) {
                break;
            }
            marker = out.getNextPartNumberMarker();
        }
        res.sort(Comparator.comparing(PartInfo::getPartNumber));
        return res;
    }

    @Override
    @SneakyThrows
    public String completeMultipartUpload(String objectName, String uploadId, Map<Integer, String> parts,
                                          long fileSize) {
        List<UploadedPartV2> uploaded = parts.entrySet().stream()
                .map(e -> new UploadedPartV2().setPartNumber(e.getKey()).setEtag(e.getValue()))
                .collect(Collectors.toList());
        client().completeMultipartUpload(new CompleteMultipartUploadV2Input()
                .setBucket(bucketName).setKey(objectName).setUploadID(uploadId)
                .setUploadedParts(uploaded));
        String url = buildAccessUrl(objectName);
        record(objectName, null, url, fileSize);
        return url;
    }

    @Override
    @SneakyThrows
    public void abortMultipartUpload(String objectName, String uploadId) {
        client().abortMultipartUpload(new AbortMultipartUploadInput()
                .setBucket(bucketName).setKey(objectName).setUploadID(uploadId));
    }
}
