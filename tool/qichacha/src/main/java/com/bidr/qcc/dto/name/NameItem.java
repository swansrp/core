package com.bidr.qcc.dto.name;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: NameItem
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:41
 */
@Data
public class NameItem {
    @ApiModelProperty("企业名称")
    @JsonProperty("Name")
    private String name;

    @ApiModelProperty("匹配原因")
    @JsonProperty("HitReason")
    private String hitReason;
}
