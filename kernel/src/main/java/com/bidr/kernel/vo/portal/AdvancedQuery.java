package com.bidr.kernel.vo.portal;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
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

    public final static String AND = "0";
    public final static String OR = "1";
    @ApiModelProperty("条件列表")
    private List<AdvancedQuery> conditionList;
    @ApiModelProperty("0 and 1 or")
    private String andOr;

    public AdvancedQuery() {
        super();
        conditionList = new ArrayList<>();
    }

    public <T, R> AdvancedQuery(GetFunc<T, R> field, Object obj) {
        super(field, obj);
        conditionList = new ArrayList<>();
    }

    public AdvancedQuery(String field, Object obj) {
        super(field, obj);
        conditionList = new ArrayList<>();
    }

    public <T, R> void addCondition(GetFunc<T, R> field, PortalConditionDict relation, List<?> data) {
        addCondition(LambdaUtil.getFieldNameByGetFunc(field), relation, data);
    }

    public void addCondition(String property, PortalConditionDict relation, List<?> data) {
        addCondition(property, relation.getValue(), data);
    }

    public void addCondition(String property, Integer relation, List<?> data) {
        if (FuncUtil.isNotEmpty(data) && FuncUtil.isNotEmpty(data.get(0))) {
            AdvancedQuery condition = new AdvancedQuery();
            condition.setProperty(property);
            condition.setRelation(relation);
            condition.setValue(data);
            this.conditionList.add(condition);
        }
    }

    public void addCondition(ConditionVO condition) {
        addCondition(condition.getProperty(), condition.getRelation(), condition.getValue());
    }
}
