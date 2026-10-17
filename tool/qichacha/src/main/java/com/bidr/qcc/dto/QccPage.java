package com.bidr.qcc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: QccPage
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 14:35
 */
@Data
public class QccPage {
    @JsonProperty("PageSize")
    private Integer pageSize;
    @JsonProperty("PageIndex")
    private Integer pageIndex;
    @JsonProperty("TotalRecords")
    private Integer totalRecords;
}
