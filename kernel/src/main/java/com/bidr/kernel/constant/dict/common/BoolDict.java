package com.bidr.kernel.constant.dict.common;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: BoolDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/12/3 11:21
 * @description Project Name: Seed
 * @Package: com.srct.service.constant.dict
 */

@MetaDict(value = "BOOLEAN_DICT", remark = "是否字典")
@Getter
@AllArgsConstructor
public enum BoolDict implements Dict {
    /**
     * 是非字典表
     */
    YES("1", "是", 1),
    NO("0", "否", 2);

    private final String value;
    private final String label;
    private final Integer order;
}
