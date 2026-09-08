package com.bidr.oss.service.impl;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.AbortMultipartUploadRequest;
import com.aliyun.oss.model.CompleteMultipartUploadRequest;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.InitiateMultipartUploadRequest;
import com.aliyun.oss.model.ListPartsRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PartETag;
import com.aliyun.oss.model.PartListing;
import com.aliyun.oss.model.PartSummary;
import com.aliyun.oss.model.ResponseHeaderOverrides;
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Title: OssAliServiceImpl
 * Description: 阿里云对象存储（OSS）实现，官方 aliyun-sdk-oss。
 * 客户端持有连接池，懒加载单例复用；私有桶访问统一走预签名
 * （下载名一致 / 内联预览语义与 TOS 实现对齐）
 * Copyright: Copyright (c) 2026 Company: plsintec Ltd.
 *
 * @author sharp
 * @since 2024/02/25 00:45
 */
@Slf4j
@Service
public class OssAliServiceImpl extends BaseOssService {

    /** 读取预签名有效期：1 小时（每次访问实时签发，避免长期签名过期/泄露风险） */
    private static final int READ_EXPIRE_SECONDS = 3600;

    /** OSS 客户端持有连接池，懒加载单例复用 */
    private volatile OSS ossClient;

    private OSS client() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    ossClient = new OSSClientBuilder().build(endpoint, appKey, appSecret);
                }
            }
        }
        return ossClient;
    }

    @Override
    public String buildAccessUrl(String objectName) {
        return endpoint + OssConst.SEP + bucketName + OssConst.SEP + objectName;
    }

    @Override
    public UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type,
                            String fileName) {
        String objectName = buildObjectName(request, file, folder, type, fileName);
        try {
            log.info("添加对象存储: {}", objectName);
            // Content-Type 必须显式设置：默认 octet-stream 会让浏览器（含预览 iframe）只能下载
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(resolveContentType(file.getContentType(), objectName));
            client().putObject(bucketName, objectName, file.getInputStream(), metadata);
        } catch (Exception e) {
            throw new ServiceException("上传文件失败", e);
        }
        String url = buildAccessUrl(objectName);
        SaObjectStorage record = record(objectName, type, url, file.getSize());
        return buildUploadVO(record);
    }

    @Override
    public void delete(Long id) {

    }

    @Override
    public void deleteObject(String url) {
        String key = getKey(url);
        log.info("deleteObject == {}", key);
        client().deleteObject(bucketName, key);
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
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(bucketName, key, HttpMethod.GET);
        request.setExpiration(new Date(System.currentTimeMillis() + READ_EXPIRE_SECONDS * 1000L));
        ResponseHeaderOverrides overrides = new ResponseHeaderOverrides();
        boolean overrideSet = false;
        if (inline) {
            String mime = contentTypeOf(key);
            if (mime != null) {
                overrides.setContentType(mime);
                overrideSet = true;
            }
        }
        if (fileName != null && !fileName.isEmpty()) {
            overrides.setContentDisposition(inline ? inlineDisposition(fileName) : contentDisposition(fileName));
            overrideSet = true;
        }
        if (overrideSet) {
            request.setResponseHeaders(overrides);
        }
        return client().generatePresignedUrl(request).toString();
    }

    // ===================== 分片上传（断点续传） =====================

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    @Override
    public String initMultipartUpload(String objectName) {
        try {
            // 分片对象同样显式设置 Content-Type（扩展名在 objectName 中保留），
            // 避免合并后元数据为 octet-stream
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(resolveContentType(null, objectName));
            return client().initiateMultipartUpload(
                    new InitiateMultipartUploadRequest(bucketName, objectName, metadata)).getUploadId();
        } catch (Exception e) {
            throw new ServiceException("初始化分片上传失败", e);
        }
    }

    @Override
    public String uploadPart(String objectName, String uploadId, int partNumber, InputStream in, long partSize) {
        try {
            UploadPartRequest request = new UploadPartRequest(bucketName, objectName, uploadId, partNumber, in, partSize);
            return client().uploadPart(request).getETag();
        } catch (Exception e) {
            throw new ServiceException("上传分片失败", e);
        }
    }

    @Override
    public List<PartInfo> listUploadedParts(String objectName, String uploadId) {
        try {
            List<PartInfo> res = new ArrayList<>();
            ListPartsRequest request = new ListPartsRequest(bucketName, objectName, uploadId);
            request.setMaxParts(1000);
            PartListing listing;
            do {
                listing = client().listParts(request);
                for (PartSummary part : listing.getParts()) {
                    res.add(new PartInfo(part.getPartNumber(), part.getETag()));
                }
                request.setPartNumberMarker(listing.getNextPartNumberMarker());
            } while (listing.isTruncated());
            res.sort(Comparator.comparing(PartInfo::getPartNumber));
            return res;
        } catch (Exception e) {
            throw new ServiceException("查询分片失败", e);
        }
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, Map<Integer, String> parts,
                                          long fileSize) {
        try {
            List<PartETag> tags = parts.entrySet().stream()
                    .map(e -> new PartETag(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());
            client().completeMultipartUpload(
                    new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, tags));
            String url = buildAccessUrl(objectName);
            record(objectName, null, url, fileSize);
            return url;
        } catch (Exception e) {
            throw new ServiceException("合并分片失败", e);
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            client().abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, objectName, uploadId));
        } catch (Exception e) {
            log.warn("中止分片上传失败: {}", objectName, e);
        }
    }
}
