package com.bidr.qcc.dto.name;

import com.bidr.qcc.dto.QccReq;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: NameSearchReq
 * Description: Copyright: Copyright (c) 2022 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2024/4/18 9:14
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NameSearchReq extends QccReq {
    @ApiModelProperty("企业名称（模糊匹配）")
    private String searchName;
}
