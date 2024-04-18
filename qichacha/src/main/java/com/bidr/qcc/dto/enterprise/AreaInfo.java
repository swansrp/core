package com.bidr.qcc.dto.enterprise;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: Area
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 11:07
 */
@Data
public class AreaInfo {
    @ApiModelProperty("省份")
    @JsonProperty("Province")
    private String province;

    @ApiModelProperty("城市")
    @JsonProperty("City")
    private String city;

    @ApiModelProperty("区域")
    @JsonProperty("County")
    private String county;
}
