package com.bidr.oss.service.impl;

import com.bidr.oss.config.minio.MinioTemplate;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.UploadRes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: OssVolcanoServiceImpl
 * Description: 火山引擎对象存储（TOS）实现，TOS 提供 S3 兼容协议，复用 S3 客户端对接
 * oss.endpoint 需配置为 TOS 的 S3 兼容地址（如 https://tos-s3-cn-beijing.volces.com）
 * Copyright: Copyright (c) 2026 Company: plsintec Ltd.
 *
 * @author sharp
 * @since 2026/09/04 01:00
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssVolcanoServiceImpl extends BaseOssService {

    private final MinioTemplate minioTemplate;

    @Override
    public String buildAccessUrl(String objectName) {
        return endpoint + OssConst.SEP + bucketName + OssConst.SEP + objectName;
    }

    @Override
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String objectName = buildObjectName(request, file, folder, type, fileName);
        if (!minioTemplate.bucketExists(bucketName)) {
            minioTemplate.createBucket(bucketName);
        }
        minioTemplate.putObject(objectName, file);
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
    public String getReadUrl(String url) {
        String key = getKey(url);
        log.info("getReadUrl == {}", key);
        return minioTemplate.getObjectLink(bucketName, key);
    }
}
