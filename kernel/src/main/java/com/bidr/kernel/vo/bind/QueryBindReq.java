package com.bidr.kernel.vo.bind;

import com.bidr.kernel.vo.portal.QueryConditionReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryBindReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/06 10:56
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryBindReq extends QueryConditionReq {
    private Object entityId;
}
