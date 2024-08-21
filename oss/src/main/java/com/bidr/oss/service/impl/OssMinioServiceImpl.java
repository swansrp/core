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
 * Title: OssMinioServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 23:53
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssMinioServiceImpl extends BaseOssService {
    private final MinioTemplate minioTemplate;

    @Override
    public String buildAccessUrl(String objectName) {
        return endpoint + OssConst.SEP + bucketName + OssConst.SEP + objectName;
    }

    @Override
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String objectName = buildObjectName(request, file, folder, type, fileName);
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
        String key = url.split(bucketName)[1].substring(1).split("\\?")[0];
        log.info("getReadUrl == {}", key);
        return minioTemplate.getObjectLink(bucketName, key);
    }
}
