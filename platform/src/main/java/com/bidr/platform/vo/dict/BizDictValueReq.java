package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: BizDictValueReq
 * Description: Copyright: Copyright (c) 2024 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/1/15 0:03
 */
@Data
public class BizDictValueReq {
    @ApiModelProperty("字典名称")
    private String dictName;
    @ApiModelProperty("字典项")
    private String value;
}