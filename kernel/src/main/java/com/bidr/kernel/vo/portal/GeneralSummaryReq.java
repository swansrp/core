package com.bidr.kernel.vo.portal;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
public class GeneralSummaryReq extends QueryConditionReq {
    private List<String> columns;
}
