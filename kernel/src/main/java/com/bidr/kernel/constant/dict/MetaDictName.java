package com.bidr.kernel.constant.dict;

import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;

/**
 * Title: MetaDictName
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/09/19 10:51
 */
public interface MetaDictName {
    /**
     * 获取字典英文名称
     *
     * @return
     */
    default String getDictName() {
        MetaDict annotation = this.getClass().getAnnotation(MetaDict.class);
        Validator.assertNotNull(annotation, ErrCodeSys.PA_DATA_NOT_EXIST, "字典名称");
        return annotation.value();
    }

    /**
     * 获取字典中文名称
     *
     * @return
     */
    default String getDictRemark() {
        MetaDict annotation = this.getClass().getAnnotation(MetaDict.class);
        Validator.assertNotNull(annotation, ErrCodeSys.PA_DATA_NOT_EXIST, "字典名称");
        return annotation.remark();
    }
}
