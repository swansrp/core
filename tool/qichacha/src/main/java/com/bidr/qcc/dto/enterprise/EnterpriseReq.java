package com.bidr.qcc.dto.enterprise;

import com.bidr.qcc.dto.QccReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: EnterpriseReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:14
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EnterpriseReq extends QccReq {
    @ApiModelProperty("搜索关键字（如企业名、人名、产品名、地址、电话、经营范围等）")
    private String searchKey;
    @ApiModelProperty("省份")
    private String provinceCode;
    @ApiModelProperty("城市Code(6位数代码)")
    private String cityCode;
    @ApiModelProperty("每页条数，默认为10，最大不超过20")
    private Integer pageSize;
    @ApiModelProperty("页码，默认第一页")
    private Integer pageIndex;
}
