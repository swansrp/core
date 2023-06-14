package com.bidr.kernel.constant.dict;

import com.bidr.kernel.constant.CommonConst;

/**
 * Title: Dict.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2019-7-26 22:16
 */
public interface Dict {

    /**
     * 字典值
     *
     * @return
     */
    Object getValue();

    /**
     * 字典显示
     *
     * @return
     */
    String getLabel();

    /**
     * 是否前端显示
     *
     * @return
     */
    default String getShow() {
        return CommonConst.YES;
    }

    /**
     * 字典项英文名
     *
     * @return
     */
    String name();


}
