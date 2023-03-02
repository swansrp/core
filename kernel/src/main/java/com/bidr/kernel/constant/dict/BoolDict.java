package com.bidr.kernel.constant.dict;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * Title: BoolDict
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/12/3 11:21
 * @description Project Name: Seed
 * @Package: com.srct.service.constant.dict
 */

@MetaDict("BOOLEAN_DICT")
@AllArgsConstructor
public enum BoolDict implements Dict {
    /**
     * 是非字典表
     */
    YES("1", 1, "是"),
    NO("0", 2, "否");

    @Getter
    @Setter
    private String value;
    @Getter
    @Setter
    private Integer order;
    @Getter
    @Setter
    private String label;


}
