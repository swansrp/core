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
@MetaParam("框架基础")
public enum ParamFrame implements Param {
    /**
     * 系统参数
     */
    CACHE_INIT_MODE("缓存加载模式", "1", "【出厂默认即可】系统缓存载体：1＝Redis（多实例共享，部署形态即此）、0＝进程内存（仅单机开发调试）；改后需重启生效");


    private final String title;
    private final String defaultValue;
    private final String remark;

}
