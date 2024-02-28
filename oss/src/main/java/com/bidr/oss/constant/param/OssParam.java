package com.bidr.oss.constant.param;

import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: ObjectStorageConstant
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019/10/16 21:18
 */
@Getter
@MetaParam
@AllArgsConstructor
public enum OssParam implements Param {
    /**
     *
     */
    OSS_SERVER_TYPE("对象服务器类型", "1", "Local Minio Ali"),
    OSS_BUCKET("对象存储桶名称", "oss", "上传路径 桶名称"),
    OSS_ACCESS_ENDPOINT("对象存储接入地址", "http://127.0.0.1", "访问域名"),
    OSS_ACCESS_KEY("对象存储接入key", "", "OSS APP KEY"),
    OSS_ACCESS_SECRET("对象存储接入秘钥", "", "OSS APP SECRET");


    private final String title;
    private final String defaultValue;
    private final String remark;
}
