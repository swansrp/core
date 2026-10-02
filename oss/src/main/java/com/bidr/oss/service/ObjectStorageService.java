package com.bidr.oss.service;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.oss.constant.dict.OssTypeDict;
import com.bidr.oss.dao.entity.SaObjectStorage;
import com.bidr.oss.vo.PartInfo;
import com.bidr.oss.vo.UploadRes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

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
     * 按访问地址删除对象（回收站超期清理等场景；
     * 不支持物理删除的实现保持空实现，上层降级为仅删记录并告警）
     *
     * @param url 存储访问地址（与 record/buildAccessUrl 生成的一致）
     */
    default void deleteObject(String url) {
    }

    /**
     * 根据url获取访问url
     *
     * @param url
     * @return
     */
    default String getReadUrl(String url) {
        return url;
    }

    /**
     * 根据url获取带下载文件名的访问url（预签名时附带 response-content-disposition，
     * 使浏览器下载保存名与分享/上传时的原文件名一致）
     *
     * @param url      存储访问地址
     * @param fileName 原始文件名（空则退化为不带下载名的访问url）
     * @return 预签名访问url
     */
    default String getReadUrl(String url, String fileName) {
        return getReadUrl(url);
    }

    /**
     * 根据url获取在线预览url（签名覆盖 response-content-type 与
     * response-content-disposition=inline，对象元数据缺失或为 octet-stream
     * 的存量文件也能内联展示而非触发下载，保存名=原文件名）
     *
     * @param url      存储访问地址
     * @param fileName 原始文件名（可空）
     * @return 预签名预览url
     */
    default String getPreviewUrl(String url, String fileName) {
        return getReadUrl(url);
    }

    // ===================== 按对象名直写/直读字节 SPI =====================
    // 服务端自产产物（技能包、解析产物等）既没有 MultipartFile 也没有 HTTP 请求上下文，
    // 又不该绑死某一家 SDK——入口通用化，落点仍是同一桶，读回可继续走
    // buildAccessUrl + getReadUrl。默认不支持，由实现按自身能力覆写，
    // 调用方拿到异常即是"当前部署形态不支持"，消息已含对象名，不必再加能力位。

    /**
     * 按对象名直写字节（不落 sa_object_storage 台账、不生成随机访问名，
     * 对象名由调用方自拼并保证稳定；版本化产物据此可重复覆盖同一 key）
     *
     * @param objectName  对象名
     * @param bytes       内容
     * @param contentType MIME，空则按扩展名推断（本机实现没有对象元数据概念，该值只是提示）
     */
    default void putBytes(String objectName, byte[] bytes, String contentType) {
        throw new ServiceException("当前对象存储不支持按对象名直写字节：" + objectName);
    }

    /**
     * 按对象名读回字节（对象不存在应抛出并带对象名，调用方要能分清"没有这个对象"与"通道坏了"）
     *
     * @param objectName 对象名
     * @return 对象内容
     */
    default byte[] readBytes(String objectName) {
        throw new ServiceException("当前对象存储不支持按对象名读回字节：" + objectName);
    }

    // ===================== 分片上传（断点续传） SPI =====================
    // 默认不支持，由具体实现按 SDK 能力覆写；上层据此降级为整文件直传

    /**
     * 是否支持分片上传（断点续传）；不支持时上层应降级为整文件直传
     */
    default boolean supportsMultipart() {
        return false;
    }

    /**
     * 初始化分片上传会话
     *
     * @param objectName 对象名
     * @return uploadId
     */
    default String initMultipartUpload(String objectName) {
        throw new UnsupportedOperationException("当前对象存储不支持分片上传");
    }

    /**
     * 上传单个分片
     *
     * @param objectName 对象名
     * @param uploadId   分片会话id
     * @param partNumber 分片号（从1开始）
     * @param in         分片数据流
     * @param partSize   分片大小（字节）
     * @return 分片etag
     */
    default String uploadPart(String objectName, String uploadId, int partNumber, InputStream in, long partSize) {
        throw new UnsupportedOperationException("当前对象存储不支持分片上传");
    }

    /**
     * 查询已上传分片（断点续传时跳过已传部分）
     *
     * @param objectName 对象名
     * @param uploadId   分片会话id
     * @return 按 partNumber 升序的分片列表
     */
    default List<PartInfo> listUploadedParts(String objectName, String uploadId) {
        throw new UnsupportedOperationException("当前对象存储不支持分片上传");
    }

    /**
     * 合并分片并返回访问地址
     *
     * @param objectName 对象名
     * @param uploadId   分片会话id
     * @param parts      分片号→etag
     * @param fileSize   文件总大小（字节，用于对象记录）
     * @return 访问地址
     */
    default String completeMultipartUpload(String objectName, String uploadId, Map<Integer, String> parts,
                                           long fileSize) {
        throw new UnsupportedOperationException("当前对象存储不支持分片上传");
    }

    /**
     * 中止分片会话并清理已传分片
     *
     * @param objectName 对象名
     * @param uploadId   分片会话id
     */
    default void abortMultipartUpload(String objectName, String uploadId) {
        throw new UnsupportedOperationException("当前对象存储不支持分片上传");
    }


}
