package com.bidr.authorization.vo.subsystem;

import com.bidr.kernel.vo.query.QueryReqVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QuerySubsystemReq
 * Description: 应用管理 分页查询请求体。
 *
 * @author Sharp
 * @since 2026-11-06
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuerySubsystemReq extends QueryReqVO {

    private String appCode;

    private String name;

    private String status;
}
