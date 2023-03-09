package com.bidr.kernel.constant.dict;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: CorrectDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/12/3 11:59
 * @description Project Name: Seed
 * @Package: com.srct.service.constant.dict
 */
@Getter
@MetaDict("CORRECT_DICT")
@AllArgsConstructor
public enum CorrectDict implements Dict {
    /**
     * 正误字典表
     */
    CORRECT("1", 1, "正确"),
    INCORRECT("0", 2, "错误");

    private final String value;
    private final Integer order;
    private final String label;


}
