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
    @ApiModelProperty("查询条件")
    private AdvancedQuery condition;
    @ApiModelProperty("排序")
    private List<SortVO> sortList;
}
