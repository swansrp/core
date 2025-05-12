package com.bidr.kernel.vo.portal;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.vo.query.QueryReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Title: QueryConditionReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 15:41
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class QueryConditionReq extends QueryReqVO {
    private List<ConditionVO> conditionList;
    private List<SortVO> sortList;

    public <T, R> void addCondition(GetFunc<T, R> field, Object obj) {
        if (FuncUtil.isEmpty(conditionList)) {
            conditionList = new ArrayList<>();
        }
        conditionList.add(new ConditionVO(LambdaUtil.getFieldNameByGetFunc(field), PortalConditionDict.EQUAL.getValue(),
                Collections.singletonList(obj)));
    }

    public <T, R> void addCondition(String field, Object obj) {
        if (FuncUtil.isEmpty(conditionList)) {
            conditionList = new ArrayList<>();
        }
        conditionList.add(new ConditionVO(field, PortalConditionDict.EQUAL.getValue(), Collections.singletonList(obj)));
    }

    public void addCondition(ConditionVO condition) {
        if (FuncUtil.isEmpty(conditionList)) {
            conditionList = new ArrayList<>();
        }
        conditionList.add(condition);
    }

    public <T, R> void addCondition(GetFunc<T, R> field, PortalConditionDict relation, List<?> data) {
        addCondition(LambdaUtil.getFieldNameByGetFunc(field), relation, data);
    }

    public void addCondition(String property, PortalConditionDict relation, List<?> data) {
        if (FuncUtil.isNotEmpty(data) && FuncUtil.isNotEmpty(data.get(0))) {
            AdvancedQuery condition = new AdvancedQuery();
            condition.setProperty(property);
            condition.setRelation(relation.getValue());
            condition.setValue(data);
            this.conditionList.add(condition);
        }
    }
}
