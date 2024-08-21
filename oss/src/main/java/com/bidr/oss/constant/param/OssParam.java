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
    OSS_SERVER_TYPE("对象服务器类型", "1", "Local Minio Ali");


    private final String title;
    private final String defaultValue;
    private final String remark;
}
