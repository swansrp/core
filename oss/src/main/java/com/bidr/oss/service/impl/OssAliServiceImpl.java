package com.bidr.oss.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.UploadRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: OssAliServiceImpl
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/25 00:45
 */
@Slf4j
@Service
public class OssAliServiceImpl extends BaseOssService {
    private OSS getOss() {
        return new OSSClientBuilder().build(endpoint, appKey, appSecret);
    }

    @Override
    public String buildAccessUrl(String objectName) {
        return endpoint + OssConst.SEP + objectName;
    }

    @Override
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        OSS client = getOss();
        String objectName = buildObjectName(request, file, folder, type, fileName);
        try {
            log.info("添加对象存储: {}", objectName);
            client.putObject(bucketName, objectName, file.getInputStream());
        } catch (Exception e) {
            throw new ServiceException("上传文件失败", e);
        } finally {
            client.shutdown();
        }
        String url = buildAccessUrl(objectName);
        SaObjectStorage record = record(objectName, type, url, file.getSize());
        return buildUploadVO(record);
    }

    @Override
    public void delete(Long id) {

    }
}
