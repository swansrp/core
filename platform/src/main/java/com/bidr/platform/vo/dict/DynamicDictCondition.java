package com.bidr.platform.vo.dict;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * 动态字典筛选条件
 * <p>
 * 支持多种操作符：=、!=、IS NULL、IS NOT NULL、LIKE
 *
 * @author Sharp
 * @since 2026-07-14
 */
@ApiModel(description = "动态字典筛选条件")
@Data
public class DynamicDictCondition {

    @ApiModelProperty(value = "列名", required = true)
    private String column;

    @ApiModelProperty(value = "操作符（=、!=、IS NULL、IS NOT NULL、LIKE）", required = true)
    private String operator;

    @ApiModelProperty(value = "值（IS NULL/IS NOT NULL 时可留空）")
    private String value;
}
