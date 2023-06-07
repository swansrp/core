package com.bidr.platform.vo.params;

import com.bidr.kernel.vo.query.QueryReqVO;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: QuerySysConfigReq
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/07 15:58
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QuerySysConfigReq extends QueryReqVO {
    @ApiModelProperty("参数名")
    private String name;
}
