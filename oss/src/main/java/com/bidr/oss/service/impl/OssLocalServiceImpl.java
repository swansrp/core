package com.bidr.oss.service.impl;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.constant.param.OssParam;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.UploadRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Title: OssLocalServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/25 23:23
 */
@Slf4j
@Service
public class OssLocalServiceImpl extends BaseOssService {
    @Override
    public String buildAccessUrl(String objectName) {
        String bucketName = getUploadPath();
        String accessDomain = sysConfigCacheService.getSysConfigValue(OssParam.OSS_ACCESS_ENDPOINT);
        return accessDomain + bucketName + OssConst.SEP + objectName;
    }

    @Override
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String uploadPath = getUploadPath();
        String objectName = buildObjectName(request, file, folder, type, fileName);
        try {
            InputStream inputStream = file.getInputStream();
            Path path = Paths.get(uploadPath);
            Path directory = path.resolve(objectName).getParent();
            if (!Files.exists(directory)) {
                Files.createDirectories(directory);
            }
            log.info("folderName: {}, fileName: {}", uploadPath, objectName);
            Files.copy(inputStream, path.resolve(objectName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceException("上传文件失败", e);
        }
        String url = buildAccessUrl(objectName);
        SaObjectStorage record = record(objectName, type, url, file.getSize());
        return buildUploadVO(record);
    }

    private String getUploadPath() {
        String bucket = sysConfigCacheService.getSysConfigValue(OssParam.OSS_BUCKET);
        return OssConst.SEP + "oss" + OssConst.SEP + bucket;
    }

    @Override
    public void delete(Long id) {

    }
}
