package com.bidr.authorization.vo.admin;

import com.bidr.kernel.vo.query.QueryReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QueryRoleReq
 * Description: Copyright: Copyright (c) 2023 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/05/05 09:15
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryRoleReq extends QueryReqVO {
    private String name;
}
