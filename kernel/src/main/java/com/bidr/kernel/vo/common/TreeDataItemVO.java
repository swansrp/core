package com.bidr.kernel.vo.common;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Title: TreeDataItemVO
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/01/10 09:42
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TreeDataItemVO {
    @JsonProperty("key")
    private Object id;
    private Object pid;
    @JsonProperty("title")
    private String name;
}
