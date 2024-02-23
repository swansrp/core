package com.bidr.kernel.vo.portal;

import com.bidr.kernel.common.func.GetFunc;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

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

    public AdvancedQuery() {
        super();
        conditionList = new ArrayList<>();
    }

    public AdvancedQuery(GetFunc field, Object obj) {
        super(field, obj);
        conditionList = new ArrayList<>();
    }

    public AdvancedQuery(String field, Object obj) {
        super(field, obj);
        conditionList = new ArrayList<>();
    }
}
