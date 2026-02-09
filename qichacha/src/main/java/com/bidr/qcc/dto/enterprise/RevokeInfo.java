package com.bidr.qcc.dto.enterprise;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.Date;

/**
 * Title: RevokeInfo
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 11:03
 */
@Data
public class RevokeInfo {
    @ApiModelProperty("注销日期")
    @JsonProperty("CancelDate")
    private Date cancelDate;

    @ApiModelProperty("注销原因")
    @JsonProperty("CancelReason")
    private String cancelReason;

    @ApiModelProperty("吊销日期")
    @JsonProperty("RevokeDate")
    private Date revokeDate;

    @ApiModelProperty("吊销原因")
    @JsonProperty("RevokeReason")
    private String revokeReason;
}
