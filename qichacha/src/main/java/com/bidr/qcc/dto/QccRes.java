package com.bidr.qcc.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: QccRes
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:29
 */
@Data
public class QccRes<T> {
    @JsonProperty("Status")
    private String status;
    @JsonProperty("Message")
    private String message;
    @JsonProperty("Paging")
    private QccPage paging;
    @JsonProperty("Result")
    private T result;
}
