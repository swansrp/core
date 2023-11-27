package com.bidr.neo4j.vo.configuration;

import com.diboot.core.binding.annotation.BindDict;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Title: PropertiesRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/08 15:03
 */
@Data
public class PropertiesRes {

    @ApiModelProperty("字段代码")
    private String property;

    @ApiModelProperty("字段名")
    private String name;

    @ApiModelProperty("字段类型")
    private Integer type;

    @ApiModelProperty("涉及字典")
    private String reference;

    @BindDict(type = "PORTAL_SUPPORT_CONDITION_DICT", field = "type")
    @ApiModelProperty("支持查询条件")
    private String condition;
}
