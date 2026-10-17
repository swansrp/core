package com.bidr.authorization.vo.partner;

import com.bidr.kernel.vo.query.QueryReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryPartnerReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/31 08:42
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryPartnerReq extends QueryReqVO {
    private String platform;
}

