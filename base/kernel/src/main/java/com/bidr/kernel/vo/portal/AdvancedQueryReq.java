package com.bidr.kernel.vo.portal;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

/**
 * Title: AdvancedQueryReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/15 09:06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdvancedQueryReq extends QueryReqVO {
    @ApiModelProperty("动态字段逻辑")
    private Map<String, Object> selectColumnCondition;
    @ApiModelProperty("查询条件")
    private AdvancedQuery condition;
    @ApiModelProperty("排序")
    private List<SortVO> sortList;
    @ApiModelProperty("返回字段列表")
    private List<String> selectColumnList;
    @ApiModelProperty("去重")
    private String distinct;

    public AdvancedQueryReq() {
        super();
    }

    public AdvancedQueryReq(AdvancedQuery condition) {
        this.condition = condition;
    }

    public AdvancedQueryReq(AdvancedQuery condition, List<SortVO> sortList) {
        this.condition = condition;
        this.sortList = sortList;
    }

    public AdvancedQueryReq(AdvancedQuery condition, List<SortVO> sortList, Map<String, Object> selectColumnCondition) {
        this.condition = condition;
        this.sortList = sortList;
        this.selectColumnCondition = selectColumnCondition;
    }

    public AdvancedQueryReq(AdvancedQuery condition, List<SortVO> sortList, Long currentPage, Long pageSize) {
        super(currentPage, pageSize);
        this.condition = condition;
        this.sortList = sortList;
    }

    public AdvancedQueryReq(AdvancedQuery condition, List<SortVO> sortList, Map<String, Object> selectColumnCondition,
                            Long currentPage, Long pageSize) {
        super(currentPage, pageSize);
        this.condition = condition;
        this.sortList = sortList;
        this.selectColumnCondition = selectColumnCondition;
    }
}
