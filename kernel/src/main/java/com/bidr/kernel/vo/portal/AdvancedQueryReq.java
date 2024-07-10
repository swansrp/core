package com.bidr.kernel.vo.portal;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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
    @ApiModelProperty("查询条件")
    private AdvancedQuery condition;
    @ApiModelProperty("排序")
    private List<SortVO> sortList;

    public AdvancedQueryReq() {
        super();
    }

    public AdvancedQueryReq(AdvancedQuery condition, List<SortVO> sortList, Long currentPage, Long pageSize) {
        super(currentPage, pageSize);
        this.condition = condition;
        this.sortList = sortList;
    }
}
