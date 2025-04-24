package com.bidr.kernel.vo.portal;

import lombok.Data;
import lombok.EqualsAndHashCode;

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
public class AdvancedStatisticReq extends AdvancedQueryReq {
    private String order;
    private List<String> groupByColumn;
    private String statisticColumn;
}
