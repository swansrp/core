package com.bidr.oss.service.impl;

import com.bidr.oss.config.minio.MinioTemplate;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.UploadRes;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Title: OssMinioServiceImpl
 * Description: MinIO/S3 兼容存储实现。
 * minio-java 不暴露低级 multipart API，分片上传不支持（上层自动降级为整文件直传）；
 * 私有桶访问统一走预签名（下载名一致 / 内联预览语义与 TOS 实现对齐）
 * Copyright: Copyright (c) 2026 Company: plsintec Ltd.
 *
 * @author sharp
 * @since 2024/02/24 23:53
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssMinioServiceImpl extends BaseOssService {

    /** 读取预签名有效期：1 小时（每次访问实时签发，避免长期签名过期/泄露风险） */
    private static final int READ_EXPIRE_SECONDS = 3600;

    private final MinioTemplate minioTemplate;

    @Override
    public String buildAccessUrl(String objectName) {
        return endpoint + OssConst.SEP + bucketName + OssConst.SEP + objectName;
    }

    @Override
    @SneakyThrows
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String objectName = buildObjectName(request, file, folder, type, fileName);
        // Content-Type 必须显式设置（octet-stream/缺失时按扩展名推断）：
        // 默认 octet-stream 会让浏览器（含预览 iframe）只能下载
        minioTemplate.putObject(bucketName, objectName, file.getInputStream(),
                resolveContentType(file.getContentType(), objectName));
        String url = buildAccessUrl(objectName);
        SaObjectStorage oss = record(objectName, type, url, file.getSize());
        UploadRes uploadRes = buildUploadVO(oss);
        uploadRes.setUrl(minioTemplate.getObjectLink(oss.getKey()));
        return uploadRes;
    }

    @Override
    public void delete(Long id) {

    }

    @Override
    public void deleteObject(String url) {
        String key = getKey(url);
        log.info("deleteObject == {}", key);
        minioTemplate.removeObject(bucketName, key);
    }

    @Override
    public String getReadUrl(String url) {
        return getReadUrl(url, null);
    }

    @Override
    public String getReadUrl(String url, String fileName) {
        log.info("getReadUrl == {}", getKey(url));
        return presign(getKey(url), fileName, false);
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
        return presign(key, fileName, true);
    }

    /** 生成 GET 预签名地址（私有桶可匿名访问；inline 时覆盖响应 Content-Type 与内联下载头） */
    private String presign(String key, String fileName, boolean inline) {
        Map<String, String> query = new HashMap<>();
        if (inline) {
            String mime = contentTypeOf(key);
            if (mime != null) {
                query.put("response-content-type", mime);
            }
        }
        if (fileName != null && !fileName.isEmpty()) {
            query.put("response-content-disposition", inline ? inlineDisposition(fileName) : contentDisposition(fileName));
        }
        return query.isEmpty()
                ? minioTemplate.getObjectLink(bucketName, key, READ_EXPIRE_SECONDS, TimeUnit.SECONDS)
                : minioTemplate.getObjectLink(bucketName, key, READ_EXPIRE_SECONDS, TimeUnit.SECONDS, query);
    }
}
