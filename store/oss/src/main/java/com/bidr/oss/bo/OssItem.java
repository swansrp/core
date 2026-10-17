package com.bidr.oss.bo;

import lombok.Data;

import java.time.LocalDate;
import java.util.Map;

/**
 * Title: OssItem
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/24 14:56
 */
@Data
public class OssItem {

    private String etag;

    /**
     * 对象名
     */
    private String objectName;

    /**
     * 对象(文件)更新时间
     */
    private LocalDate lastModified;

    private long size;

    private String storageClass;

    private boolean isLatest;

    private String versionId;

    /**
     * 对象元信息
     */
    private Map<String, String> userMetadata;

    /**
     * 是否为文件夹
     */
    private boolean isDir = false;

}

