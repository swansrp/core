package com.bidr.kernel.vo.portal;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: AdvanceQuery
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 09:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdvanceQuery extends ConditionVO {
    @ApiModelProperty("前驱条件组")
    private AdvanceQuery conditionA;
    @ApiModelProperty("0 and 1 or")
    private String andOr;
    @ApiModelProperty("后续条件组")
    private AdvanceQuery conditionB;
}
