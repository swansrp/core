package com.bidr.platform.vo.dict;


import lombok.Data;

import java.util.List;

/**
 * @author Sharp
 * @since 2026/3/5 17:27
 */
@Data
public class BizDictRes {
    private String dictCode;
    private String dictName;

    private List<BizDictVO> dictItemList;
}
