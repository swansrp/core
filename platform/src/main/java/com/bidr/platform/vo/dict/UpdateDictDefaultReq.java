package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: UpdateDictDefaultReq
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/29 11:34
 */
@Data
public class UpdateDictDefaultReq {
    @ApiModelProperty("字典名")
    private String dictName;
    @ApiModelProperty("default字典项id")
    private String dictId;
}
