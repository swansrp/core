/**
 * Title: BaseVO.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author sharuopeng
 * @since 2019-02-19 13:51:27
 */
package com.bidr.kernel.vo.query;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author sharuopeng
 */
@Data
public class QueryReqVO {
    @ApiModelProperty("当前页")
    private Long currentPage;
    @ApiModelProperty("每页大小")
    private Long pageSize;

    public QueryReqVO() {
        this.currentPage = 1L;
        this.pageSize = 20L;
    }

    public QueryReqVO(Long currentPage, Long pageSize) {
        this.currentPage = currentPage;
        this.pageSize = pageSize;
    }

}
