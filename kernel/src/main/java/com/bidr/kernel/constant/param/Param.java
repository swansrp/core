package com.bidr.kernel.constant.param;

/**
 * Title: Param
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2022/6/8 17:03
 */
public interface Param {
    /**
     * 参数key
     *
     * @return
     */
    String name();

    /**
     * 参数名称
     *
     * @return
     */
    String getTitle();

    /**
     * 默认值
     *
     * @return
     */
    String getDefaultValue();

    /**
     * 参数注释
     *
     * @return
     */
    default String getRemark() {
        return "";
    }

}
