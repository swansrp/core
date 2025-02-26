package com.bidr.oss.service;

import com.bidr.oss.constant.dict.OssTypeDict;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.vo.UploadRes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;

/**
 * Title: ObjectStorageService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019/10/16 21:09
 */
public interface ObjectStorageService {

    /**
     * 生成对象名称
     *
     * @param request  请求
     * @param file     文件
     * @param folder   文件夹名称
     * @param type     文件类型
     * @param fileName 指定文件名
     * @return 对象名称
     */
    String buildObjectName(HttpServletRequest request, MultipartFile file, String folder, String type, String fileName);

    /**
     * 获取上传文件类型
     *
     * @param fileName 文件名
     * @param type 文件类型
     * @return 文件类型
     */
    OssTypeDict getFileType(String fileName, String type);

    /**
     * 生成文件名
     *
     * @param originalName 原文件名
     * @param ip           访问ip
     * @return 生成文件名
     */
    String buildFileName(String originalName, String ip);

    /**
     * 格式化接受文件名
     *
     * @param fileName 文件名
     * @return 符合要求的文件名
     */
    String formatFileName(String fileName);

    /**
     * 构建返回类型
     *
     * @param oss 对象信息
     * @return 返回类型
     */
    UploadRes buildUploadVO(SaObjectStorage oss);


    /**
     * 上传文件并存储
     *
     * @param objectName 文件名
     * @return 返回类型
     */
    String buildAccessUrl(String objectName);

    /**
     * 上传文件并存储
     *
     * @param request  http请求
     * @param file     文件
     * @param folder   文件夹
     * @param type     文件类型
     * @param fileName 文件名
     * @return 返回类型
     */
    UploadRes upload(HttpServletRequest request, MultipartFile file, String folder, String type, String fileName);

    /**
     * 记录对象信息
     *
     * @param name     文件名
     * @param type     类型
     * @param uri      地址
     * @param fileSize 大小
     * @return 对象信息
     */

    SaObjectStorage record(String name, String type, String uri, Long fileSize);

    /**
     * 删除上传数据
     *
     * @param id id
     */
    void delete(Long id);

    /**
     * 根据url获取访问url
     *
     * @param url
     * @return
     */
    default String getReadUrl(String url) {
        return url;
    }


}
