package com.bidr.qcc.dto.name;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: NameSearchReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:14
 */
@Data
public class NameSearchRes {
    @ApiModelProperty("数据是否存在(1-存在，0-不存在)")
    @JsonProperty("VerifyResult")
    private Integer verifyResult;
    @JsonProperty("Data")
    private List<NameItem> data;
}
