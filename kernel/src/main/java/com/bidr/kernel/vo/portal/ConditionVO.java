package com.bidr.kernel.vo.portal;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Title: ConditionVO
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:40
 */
@Data
public class ConditionVO {
    @ApiModelProperty("字段名")
    private String property;
    @ApiModelProperty("查询值")
    private List<?> value;
    @ApiModelProperty("查询关系")
    private Integer relation;
    @ApiModelProperty("日期格式")
    private String dateFormat;
}
