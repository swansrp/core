package com.bidr.kernel.vo.portal;

import com.bidr.kernel.common.func.GetFunc;
import com.bidr.kernel.constant.dict.portal.PortalConditionDict;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.LambdaUtil;
import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    @ApiModelProperty("动态字段逻辑")
    private Map<String, Object> selectColumnCondition;
    @ApiModelProperty("查询条件")
    private List<ConditionVO> conditionList;
    @ApiModelProperty("排序")
    private List<SortVO> sortList;
    @ApiModelProperty("返回字段列表")
    private List<String> selectColumnList;
    @ApiModelProperty("去重")
    private String distinct;

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
            if (FuncUtil.isEmpty(conditionList)) {
                conditionList = new ArrayList<>();
            }
            this.conditionList.add(condition);
        }
    }
}
