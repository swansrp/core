package com.bidr.kernel.vo.portal;

import com.bidr.kernel.utils.FuncUtil;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: Query
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/9/9 8:37
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Query {
    @ApiModelProperty("动态字段逻辑")
    private Map<String, Object> selectColumnCondition = new HashMap<>();
    @ApiModelProperty("高级查询条件")
    private AdvancedQuery condition = new AdvancedQuery();
    @ApiModelProperty("普通查询条件")
    private List<ConditionVO> conditionList = new ArrayList<>();
    @ApiModelProperty("默认查询条件")
    private AdvancedQuery defaultQuery = new AdvancedQuery();;
    @ApiModelProperty("排序")
    private List<SortVO> sortList = new ArrayList<>();;

    public Query(AdvancedQueryReq advancedQueryReq, QueryConditionReq queryConditionReq) {
        Map<String, Object> map = advancedQueryReq.getSelectColumnCondition();
        if (map == null) {
            map = new HashMap<>();
        }
        if (FuncUtil.isNotEmpty(queryConditionReq.getSelectColumnCondition())) {
            map.putAll(queryConditionReq.getSelectColumnCondition());
        }
        this.setSelectColumnCondition(queryConditionReq.getSelectColumnCondition());
        this.setConditionList(queryConditionReq.getConditionList());
        this.setSortList(queryConditionReq.getSortList());
        this.setCondition(advancedQueryReq.getCondition());
    }

    public Query(AdvancedQueryReq advancedQueryReq) {
        this.setSelectColumnCondition(advancedQueryReq.getSelectColumnCondition());
        this.setSortList(advancedQueryReq.getSortList());
        this.setCondition(advancedQueryReq.getCondition());
    }

    public Query(QueryConditionReq queryConditionReq) {
        this.setSelectColumnCondition(queryConditionReq.getSelectColumnCondition());
        this.setConditionList(queryConditionReq.getConditionList());
        this.setSortList(queryConditionReq.getSortList());
    }

}
