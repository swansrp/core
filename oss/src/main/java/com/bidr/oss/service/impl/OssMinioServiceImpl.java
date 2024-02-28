package com.bidr.oss.service.impl;

import com.bidr.oss.config.minio.MinioTemplate;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.constant.param.OssParam;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.UploadRes;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
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
@Service
@RequiredArgsConstructor
public class OssMinioServiceImpl extends BaseOssService {
    private final MinioTemplate minioTemplate;
    private final SysConfigCacheService sysConfigCacheService;

    @Override
    public String buildAccessUrl(String objectName) {
        String accessDomain = sysConfigCacheService.getSysConfigValue(OssParam.OSS_ACCESS_ENDPOINT);
        String bucketName = sysConfigCacheService.getSysConfigValue(OssParam.OSS_BUCKET);
        return accessDomain + OssConst.SEP + bucketName + OssConst.SEP + objectName;
    }

    @Override
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String objectName = buildObjectName(request, file, folder, type, fileName);
        minioTemplate.putObject(objectName, file);
        String url = buildAccessUrl(objectName);
        SaObjectStorage oss = record(objectName, type, url, file.getSize());
        return buildUploadVO(oss);
    }

    @Override
    public void delete(Long id) {

    }

}
