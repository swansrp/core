/**
 * Title: BaseVO.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author sharuopeng
 * @date 2019-02-19 13:51:27
 */
package com.bidr.kernel.vo.query;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author sharuopeng
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryReqVO extends ReqBaseVO {
    @ApiModelProperty("当前页")
    private Long currentPage = 1L;
    @ApiModelProperty("每页大小")
    private Long pageSize = 20L;

}
