package com.bidr.authorization.vo.partner;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * Title: PartnerRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/06/19 09:54
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartnerRes {

    @ApiModelProperty(value = "应用的唯一标识key")
    @Size(max = 50, message = "应用的唯一标识key最大长度要小于 50")
    @NotBlank(message = "应用的唯一标识key不能为空")
    private String appKey;


    @ApiModelProperty(value = "应用的密钥")
    @Size(max = 50, message = "应用的密钥最大长度要小于 50")
    @NotBlank(message = "应用的密钥不能为空")
    private String appSecret;
}
