package com.bidr.oss.service;

import com.bidr.kernel.constant.db.SqlConstant;
import com.bidr.kernel.utils.*;
import com.bidr.oss.constant.OssConst;
import com.bidr.oss.constant.dict.OssTypeDict;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.dao.repository.SaObjectStorageService;
import com.bidr.oss.vo.UploadRes;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.apache.commons.lang3.StringUtils;
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

    @Resource
    protected SysConfigCacheService sysConfigCacheService;
    @Resource
    private SaObjectStorageService saObjectStorageService;

    @Override
    public String buildObjectName(HttpServletRequest request, MultipartFile file, String folder, String type,
                                  String fileName) {
        String fileType = getFileType(type).name().toLowerCase();
        String folderName = BeanUtil.getProperty("my.project.name");
        if (FuncUtil.isNotEmpty(folder)) {
            folderName = folderName + OssConst.SEP + folder;
        }
        folderName =
                folderName + OssConst.SEP + fileType + OssConst.SEP + DateUtil.formatDate(new Date(), DateUtil.DATE);
        if (FuncUtil.isEmpty(fileName)) {
            fileName = buildFileName(file.getOriginalFilename(), HttpUtil.getRemoteIp(request));
        } else {
            fileName = formatFileName(fileName);
        }
        return folderName + OssConst.SEP + fileName;
    }

    @Override
    public OssTypeDict getFileType(String type) {
        return DictEnumUtil.getEnumByValue(type, OssTypeDict.class, OssTypeDict.OTHER);
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
        String fileType = getFileType(type).getValue();
        SaObjectStorage os = new SaObjectStorage();
        String[] split = name.split("/");
        os.setName(split[split.length - 1]);
        os.setKey(name);
        os.setUri(uri + "?t=" + System.currentTimeMillis());
        os.setType(fileType);
        os.setSize(fileSize);
        os.setValid(SqlConstant.VALID);
        saObjectStorageService.insertOrUpdate(os, SaObjectStorage::getUri);
        return os;
    }

}
