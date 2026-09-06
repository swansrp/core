package com.bidr.oss.service;

import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.utils.*;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.constant.dict.OssTypeDict;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.dao.repository.SaObjectStorageService;
import com.bidr.oss.vo.UploadRes;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * Title: BaseOssService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/25 23:23
 */
@Service
public abstract class BaseOssService implements ObjectStorageService {

    @Value("${oss.appKey}")
    protected String appKey;
    @Value("${oss.appSecret}")
    protected String appSecret;
    @Value("${oss.endpoint}")
    protected String endpoint;
    @Value("${oss.bucket}")
    protected String bucketName;
    @Resource
    private SaObjectStorageService saObjectStorageService;

    @Override
    public String buildObjectName(HttpServletRequest request, MultipartFile file, String folder, String type,
                                  String fileName) {
        if (FuncUtil.isEmpty(fileName)) {
            fileName = buildFileName(file.getOriginalFilename(), HttpUtil.getRemoteIp(request));
        } else {
            fileName = formatFileName(fileName);
        }
        return concatObjectName(folder, type, fileName);
    }

    /**
     * 生成对象名（分片上传初始化时无完整文件体，按原始文件名生成随机对象名）
     *
     * @param request      请求
     * @param originalName 原始文件名（含后缀）
     * @param folder       文件夹
     * @param type         文件类型
     * @return 对象名
     */
    public String buildObjectName(HttpServletRequest request, String originalName, String folder, String type) {
        String fileName = buildFileName(originalName, HttpUtil.getRemoteIp(request));
        return concatObjectName(folder, type, fileName);
    }

    /**
     * 按项目名/文件夹/类型/日期拼接对象名
     */
    private String concatObjectName(String folder, String type, String fileName) {
        OssTypeDict ossType = getFileType(fileName, type);
        String fileType = ossType.name().toLowerCase();
        String folderName = BeanUtil.getProperty("my.project.name");
        if (FuncUtil.isNotEmpty(folder)) {
            folderName = folderName + OssConst.SEP + folder;
        }
        folderName =
                folderName + OssConst.SEP + fileType + OssConst.SEP + DateUtil.formatDate(new Date(), DateUtil.DATE);
        return folderName + OssConst.SEP + fileName;
    }

    @Override
    public OssTypeDict getFileType(String fileName, String type) {
        OssTypeDict ossType = OssTypeDict.getByFileName(fileName);
        if (ossType.equals(OssTypeDict.OTHER)) {
            ossType = DictEnumUtil.getEnumByValue(type, OssTypeDict.class, OssTypeDict.OTHER);
        }
        return ossType;
    }

    @Override
    public String buildFileName(String originalName, String ip) {
        String suffix = "";
        if (StringUtils.isNotBlank(originalName)) {
            suffix = originalName.substring(originalName.lastIndexOf("."));
        }
        return Md5Util.MD5(originalName + ip + (new Date())) + suffix;
    }

    @Override
    public String formatFileName(String fileName) {
        if (StringUtils.isNotBlank(fileName)) {
            if (StringUtils.startsWith(fileName, OssConst.SEP)) {
                fileName = fileName.substring(1);
            }
            if (StringUtils.endsWith(fileName, OssConst.SEP)) {
                fileName = fileName.substring(0, fileName.length() - 2);
            }
        }
        return fileName;
    }

    @Override
    public UploadRes buildUploadVO(SaObjectStorage oss) {
        UploadRes res = new UploadRes();
        res.setFileName(oss.getName());
        res.setFileSize(oss.getSize());
        res.setUrl(oss.getUri());
        return res;
    }

    @Override
    public SaObjectStorage record(String name, String type, String uri, Long fileSize) {
        SaObjectStorage os = new SaObjectStorage();
        String[] split = name.split("/");
        os.setName(split[split.length - 1]);
        String fileType = getFileType(os.getName(), type).getValue();
        os.setKey(name);
        os.setUri(uri + "?t=" + System.currentTimeMillis());
        os.setType(fileType);
        os.setSize(fileSize);
        os.setValid(SqlConstant.VALID);
        saObjectStorageService.insertOrUpdate(os, SaObjectStorage::getUri);
        return os;
    }

    public String getKey(String url) {
        String path = url.split("\\?")[0];
        // path-style（endpoint/bucket/key）：bucket 为路径段
        int idx = path.indexOf(bucketName + OssConst.SEP);
        if (idx >= 0) {
            return path.substring(idx + bucketName.length() + 1);
        }
        // 虚拟主机式（bucket.endpoint/key，如 TOS 签名地址）：bucket 在子域名，取 host 后路径
        int schemeEnd = path.indexOf("//");
        int pathStart = path.indexOf('/', schemeEnd + 2);
        return pathStart >= 0 ? path.substring(pathStart + 1) : path;
    }

}
