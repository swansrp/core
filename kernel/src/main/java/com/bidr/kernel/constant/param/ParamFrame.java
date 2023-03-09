/**
 * Title: ParamFrame.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-7-26 23:18
 * @description Project Name: Grote
 * @Package: com.srct.service.constant
 */
package com.bidr.kernel.constant.param;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ParamFrame implements Param {
    /**
     * 系统参数
     */
    CACHE_INIT_MODE("1", "缓存加载模式");


    private final String defaultValue;
    private final String remark;
}
