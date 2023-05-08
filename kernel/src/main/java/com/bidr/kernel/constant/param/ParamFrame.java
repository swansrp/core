/**
 * Title: ParamFrame.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019-7-26 23:18
 * @description Project Name: Grote
 * @Package: com.srct.service.constant
 */
package com.bidr.kernel.constant.param;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@MetaParam
public enum ParamFrame implements Param {
    /**
     * 系统参数
     */
    CACHE_INIT_MODE("缓存加载模式", "1", "1:redis, 0:内存");


    private final String title;
    private final String defaultValue;
    private final String remark;

}
