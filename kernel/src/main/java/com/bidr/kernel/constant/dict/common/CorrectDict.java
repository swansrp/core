package com.bidr.kernel.constant.dict.common;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;

/**
 * Title: CorrectDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/12/3 11:59
 * @description Project Name: Seed
 * @Package: com.srct.service.constant.dict
 */
@Getter
@MetaDict(value = "CORRECT_DICT", remark = "真假字典")
@AllArgsConstructor
public enum CorrectDict implements Dict {
    /**
     * 正误字典表
     */
    CORRECT(1, "正确", 1),
    INCORRECT(0, "错误", 2);

    private final Integer value;
    private final String label;
    private final Integer order;
    private final HashMap<Object, Enum<?>> map = new HashMap<>();
}
