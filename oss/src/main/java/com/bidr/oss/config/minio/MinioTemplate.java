package com.bidr.oss.config.minio;

import cn.hutool.core.io.FastByteArrayOutputStream;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.oss.bo.OssItem;
import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import io.minio.messages.Item;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Title: MinioTemplate
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 15:15
 */
@Component
public class MinioTemplate {

    @Value("${oss.appKey}")
    private String appKey;
    @Value("${oss.appSecret}")
    private String appSecret;
    @Value("${oss.endpoint}")
    private String endpoint;
    @Value("${oss.bucket}")
    private String bucketName;
    
    private MinioClient minioClient;

    public MinioClient getClient() {
        if (FuncUtil.isEmpty(minioClient)) {
            this.minioClient = MinioClient.builder().endpoint(endpoint).credentials(appKey, appSecret).build();
        }
        return minioClient;
    }

    /**
     * 检查存储桶是否存在
     *
     * @param bucketName 存储桶名称
     * @return true: 桶存在 ；false: 桶不存在
     */
    @SneakyThrows
    public boolean bucketExists(String bucketName) {
        return getClient().bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
    }

    /**
     * 创建存储桶
     *
     * @param bucketName 存储桶名称
     * @return true: 桶存在 ；false: 桶不存在
     */
    @SneakyThrows
    public boolean createBucket(String bucketName) {
        getClient().makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        return true;
    }

    /**
     * 设置bucket的访问策略
     *
     * @param bucketName       存储桶名称
     * @param bucketPolicyJson 策略json字符串，语法参考  https://docs.aws.amazon
     *                         .com/zh_cn/AmazonS3/latest/userguide/access-policy-language-overview.html
     */
    @SneakyThrows
    public void setBucketPolicy(String bucketName, String bucketPolicyJson) {
        getClient().setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucketName).config(bucketPolicyJson).build());
    }

    /**
     * 列出所有存储桶名称
     *
     * @return 桶的名称列表
     */
    @SneakyThrows
    public List<String> listBucketNames() {
        List<Bucket> bucketList = getClient().listBuckets();
        List<String> bucketListName = new ArrayList<>();
        for (Bucket bucket : bucketList) {
            bucketListName.add(bucket.name());
        }
        return bucketListName;
    }

    /**
     * 文件上传，采用默认桶名
     *
     * @param objectName    对象名
     * @param multipartFile Spring文件对象
     */
    @SneakyThrows
    public void putObject(String objectName, MultipartFile multipartFile) {
        putObject(bucketName, objectName, multipartFile.getInputStream(), multipartFile.getContentType());
    }

    /**
     * 文件上传
     *
     * @param bucketName  桶名称
     * @param objectName  对象名
     * @param stream      文件流
     * @param contentType 未指定contentType就无法预览
     */
    @SneakyThrows
    public void putObject(String bucketName, String objectName, InputStream stream, String contentType) {
        PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucketName).object(objectName)
                .stream(stream, stream.available(), -1);
        if (contentType != null) {
            builder.contentType(contentType);
        }
        getClient().putObject(builder.build());
    }

    /**
     * 文件上传
     *
     * @param objectName 对象名
     * @param stream     文件流
     */
    @SneakyThrows
    public void putObject(String objectName, InputStream stream) {
        putObject(bucketName, objectName, stream, null);
    }

    /**
     * 文件上传，采用默认桶名
     *
     * @param objectName  对象名
     * @param stream      文件流
     * @param contentType 未指定contentType，默认为application/octet-stream。通过浏览器打开时会直接下载
     */
    @SneakyThrows
    public void putObject(String objectName, InputStream stream, String contentType) {
        putObject(bucketName, objectName, stream, contentType);
    }

    /**
     * 上传文件
     *
     * @param objectArgs 上传参数
     */
    @SneakyThrows
    public void putObject(PutObjectArgs objectArgs) {
        getClient().putObject(objectArgs);
    }


    /**
     * 获取对象的临时访问地址，默认过期时间为7天
     *
     * @param objectName 对象名称
     * @return http链接
     */
    @SneakyThrows
    public String getObjectLink(String objectName) {
        return getObjectLink(bucketName, objectName);
    }


    /**
     * 获取对象的临时访问地址，默认过期时间为7天
     *
     * @param objectName 对象名称
     * @return http链接
     */
    @SneakyThrows
    public String getObjectLink(String bucketName, String objectName) {
        GetPresignedObjectUrlArgs build = GetPresignedObjectUrlArgs.builder().bucket(bucketName).object(objectName)
                .method(Method.GET).build();
        return getClient().getPresignedObjectUrl(build);
    }

    /**
     * 获取对象的临时访问地址
     *
     * @param objectName     对象名称
     * @param expiryDuration 过期时长
     * @param unit           时长单位
     * @return http链接
     */
    @SneakyThrows
    public String getObjectLink(String objectName, int expiryDuration, TimeUnit unit) {
        return getObjectLink(bucketName, objectName, expiryDuration, unit);
    }

    /**
     * 获取对象的临时访问地址
     *
     * @param bucketName     桶名称
     * @param objectName     对象名称
     * @param expiryDuration 过期时长
     * @param unit           时长单位
     * @return http链接
     */
    @SneakyThrows
    public String getObjectLink(String bucketName, String objectName, int expiryDuration, TimeUnit unit) {
        GetPresignedObjectUrlArgs build = GetPresignedObjectUrlArgs.builder().bucket(bucketName).object(objectName)
                .expiry(expiryDuration, unit).method(Method.GET).build();
        return getClient().getPresignedObjectUrl(build);
    }

    /**
     * 查看对象 ，默认桶
     * 只列出当前对象，包括文件及文件夹
     *
     * @return 存储bucket内文件对象信息
     */
    public List<OssItem> listObjects() {
        return listObjects(bucketName, "");
    }

    /**
     * 查看文件对象
     * 只列出当前对象，包括文件及文件夹
     *
     * @param bucketName 桶名称
     * @param prefix     对象前缀
     * @return 存储bucket内文件对象信息
     */
    public List<OssItem> listObjects(String bucketName, String prefix) {

        ListObjectsArgs.Builder builder = ListObjectsArgs.builder().bucket(bucketName);
        // 设置前缀
        if (prefix != null) {
            builder.prefix(prefix);
        }
        return listObjects(builder.build());
    }

    /**
     * 查看文件对象
     * 只列出当前对象，包括文件及文件夹
     *
     * @param listObjectsArgs 查询参数
     * @return 存储bucket内文件对象信息
     */
    @SneakyThrows
    public List<OssItem> listObjects(ListObjectsArgs listObjectsArgs) {

        Iterable<Result<Item>> results = getClient().listObjects(listObjectsArgs);
        List<OssItem> items = new ArrayList<>();
        for (Result<Item> result : results) {
            Item item = result.get();
            OssItem ossItem = new OssItem();
            ossItem.setEtag(item.etag());
            ossItem.setObjectName(item.objectName());
            // 只有file才有更新时间
            if (!item.isDir()) {
                ossItem.setLastModified(item.lastModified().toLocalDate());
            }
            ossItem.setSize(item.size());
            ossItem.setStorageClass(item.storageClass());
            ossItem.setLatest(item.isLatest());
            ossItem.setVersionId(item.versionId());
            ossItem.setUserMetadata(item.userMetadata());
            ossItem.setDir(item.isDir());
            items.add(ossItem);
        }
        return items;
    }

    /**
     * 查看对象 ，默认桶
     * 只列出当前对象，包括文件及文件夹
     *
     * @param prefix 对象前缀
     * @return 存储bucket内文件对象信息
     */
    public List<OssItem> listObjects(String prefix) {
        return listObjects(bucketName, prefix);
    }

    /**
     * 获取桶对象
     *
     * @param objectName 对象名称
     * @return 对象的输入流
     */
    @SneakyThrows
    public GetObjectResponse getObject(String objectName) {
        return getObject(bucketName, objectName);
    }

    /**
     * 获取桶对象
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     * @return 对象的输入流
     */
    @SneakyThrows
    public GetObjectResponse getObject(String bucketName, String objectName) {
        GetObjectArgs objectArgs = GetObjectArgs.builder().bucket(bucketName).object(objectName).build();
        return getClient().getObject(objectArgs);
    }

    /**
     * 获取桶对象
     *
     * @param objectName 对象名称
     * @return 对象的字节数组
     */
    @SneakyThrows
    public byte[] getObjectData(String objectName) {
        return getObjectData(bucketName, objectName);
    }

    /**
     * 获取桶对象
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     * @return 对象的字节数组
     */
    @SneakyThrows
    public byte[] getObjectData(String bucketName, String objectName) {
        GetObjectResponse response = getObject(bucketName, objectName);
        byte[] buf = new byte[1024];
        int len;
        try (FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
            while ((len = response.read(buf)) != -1) {
                os.write(buf, 0, len);
            }
            os.flush();
            return os.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


    /**
     * 文件下载
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     * @param res        响应对象
     */
    @SneakyThrows
    public void download(String bucketName, String objectName, HttpServletResponse res) {
        byte[] data = getObjectData(bucketName, objectName);

        String fileName = objectName;
        int i = objectName.lastIndexOf("/");
        if (i > -1) {
            fileName = objectName.substring(i + 1);
        }
        res.setCharacterEncoding("utf-8");
        // 设置强制下载不打开
        // res.setContentType("application/force-download");
        res.addHeader("Content-Disposition", "attachment;fileName=" + fileName);
        try (ServletOutputStream stream = res.getOutputStream()) {
            stream.write(data);
            stream.flush();
        }
    }


    /**
     * 删除对象
     *
     * @param objectName 对象名称
     */
    @SneakyThrows
    public void removeObject(String objectName) {
        removeObject(bucketName, objectName);
    }

    /**
     * 删除对象
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     */
    @SneakyThrows
    public void removeObject(String bucketName, String objectName) {
        RemoveObjectArgs args = RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build();
        getClient().removeObject(args);
    }

}
