package com.bidr.kernel.vo.portal;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Title: AdvancedQuery
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 09:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdvancedQuery extends ConditionVO {
    @ApiModelProperty("条件列表")
    private List<AdvancedQuery> conditionList;
    @ApiModelProperty("0 and 1 or")
    private String andOr;
}
