package com.bidr.oss.service.impl;

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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

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
        return endpoint + bucketName + OssConst.SEP + objectName;
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
        return OssConst.SEP + "oss" + OssConst.SEP + bucketName;
    }

    @Override
    public void delete(Long id) {

    }

    // ===================== 分片上传（断点续传） =====================
    // 临时分片落盘 {上传根}/.multipart/{uploadId}/part-{n}，complete 时按序合并为目标对象

    @Override
    public boolean supportsMultipart() {
        return true;
    }

    private Path multipartDir(String uploadId) {
        return Paths.get(getUploadPath(), ".multipart", uploadId);
    }

    @Override
    public String initMultipartUpload(String objectName) {
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        try {
            Files.createDirectories(multipartDir(uploadId));
        } catch (IOException e) {
            throw new ServiceException("初始化分片上传失败", e);
        }
        return uploadId;
    }

    @Override
    public String uploadPart(String objectName, String uploadId, int partNumber, InputStream in, long partSize) {
        try {
            Path dir = multipartDir(uploadId);
            if (!Files.exists(dir)) {
                throw new ServiceException("分片会话不存在或已失效");
            }
            Files.copy(in, dir.resolve("part-" + partNumber), StandardCopyOption.REPLACE_EXISTING);
            return "part-" + partNumber;
        } catch (IOException e) {
            throw new ServiceException("上传分片失败", e);
        }
    }

    @Override
    public List<PartInfo> listUploadedParts(String objectName, String uploadId) {
        List<PartInfo> parts = new ArrayList<>();
        Path dir = multipartDir(uploadId);
        if (!Files.exists(dir)) {
            return parts;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.map(p -> p.getFileName().toString())
                    .filter(n -> n.startsWith("part-"))
                    .forEach(n -> parts.add(new PartInfo(Integer.parseInt(n.substring(5)), n)));
        } catch (IOException e) {
            throw new ServiceException("查询分片失败", e);
        }
        parts.sort(Comparator.comparing(PartInfo::getPartNumber));
        return parts;
    }

    @Override
    public String completeMultipartUpload(String objectName, String uploadId, Map<Integer, String> parts,
                                          long fileSize) {
        Path dir = multipartDir(uploadId);
        if (!Files.exists(dir)) {
            throw new ServiceException("分片会话不存在或已失效");
        }
        Path target = Paths.get(getUploadPath(), objectName);
        try {
            if (!Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                for (Integer partNumber : new TreeSet<>(parts.keySet())) {
                    Files.copy(dir.resolve("part-" + partNumber), out);
                }
            }
            long size = Files.size(target);
            String url = buildAccessUrl(objectName);
            record(objectName, null, url, size);
            return url;
        } catch (IOException e) {
            throw new ServiceException("合并分片失败", e);
        } finally {
            deleteDirectoryQuietly(dir);
        }
    }

    @Override
    public void abortMultipartUpload(String objectName, String uploadId) {
        deleteDirectoryQuietly(multipartDir(uploadId));
    }

    private void deleteDirectoryQuietly(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
