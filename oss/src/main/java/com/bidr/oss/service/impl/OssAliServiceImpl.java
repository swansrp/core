package com.bidr.oss.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.UploadPartRequest;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.service.BaseOssService;
import com.bidr.oss.vo.PartInfo;
import com.bidr.oss.vo.UploadRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // ===================== 分片上传（断点续传） =====================

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    public String initMultipartUpload(String objectName) {
        OSS client = getOss();
        try {
            return client.initiateMultipartUpload(
                    new InitiateMultipartUploadRequest(bucketName, objectName)).getUploadId();
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String uploadPart(String objectName, String uploadId, int partNumber, InputStream in, long partSize) {
        OSS client = getOss();
        try {
            UploadPartRequest request = new UploadPartRequest(bucketName, objectName, uploadId, partNumber, in, partSize);
            return client.uploadPart(request).getETag();
        } catch (Exception e) {
            throw new ServiceException("上传分片失败", e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public List<PartInfo> listUploadedParts(String objectName, String uploadId) {
        OSS client = getOss();
        try {
            List<PartInfo> res = new ArrayList<>();
            ListPartsRequest request = new ListPartsRequest(bucketName, objectName, uploadId);
            request.setMaxParts(1000);
            PartListing listing;
            do {
                listing = client.listParts(request);
                for (PartSummary part : listing.getParts()) {
                    res.add(new PartInfo(part.getPartNumber(), part.getETag()));
                }
                request.setPartNumberMarker(listing.getNextPartNumberMarker());
            } while (listing.isTruncated());
            res.sort(Comparator.comparing(PartInfo::getPartNumber));
            return res;
        } finally {
            client.shutdown();
        }
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, Map<Integer, String> parts,
                                          long fileSize) {
        OSS client = getOss();
        try {
            List<PartETag> tags = parts.entrySet().stream()
                    .map(e -> new PartETag(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            client.completeMultipartUpload(
                    new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, tags));
            String url = buildAccessUrl(objectName);
            record(objectName, null, url, fileSize);
            return url;
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        OSS client = getOss();
        try {
            client.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, objectName, uploadId));
        } finally {
            client.shutdown();
        }
    }
}
