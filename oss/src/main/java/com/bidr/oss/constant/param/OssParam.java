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
    /**
     * kkFileView 在线预览服务地址：不同客户部署地址不同，由系统参数下发前端；
     * 支持本机反代相对路径（如 /kkfileview/onlinePreview?url=）或完整外链，0/空 = 关闭预览入口
     */
    OSS_PREVIEW_URL("在线预览服务地址", "0", "kkFileView onlinePreview 地址，如 /kkfileview/onlinePreview?url=；0 或空 = 关闭预览");


    private final String title;
    private final String defaultValue;
    private final String remark;
}
