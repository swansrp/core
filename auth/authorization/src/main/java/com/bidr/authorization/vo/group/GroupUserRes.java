package com.bidr.authorization.vo.group;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Title: GroupUserRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/14 14:39
 */
@Data
public class GroupUserRes {
    @JsonProperty("value")
    private String customerNumber;

    @JsonProperty("label")
    private String name;
}
