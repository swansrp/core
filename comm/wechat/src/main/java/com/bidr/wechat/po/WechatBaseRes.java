/**
 * Title: WechatBasePO.java Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @Project Name: SpringBootCommonLib
 * @Package: com.srct.service.po.wechat
 * @author Sharp
 * @date 2019-01-30 21:31:18
 */
package com.bidr.wechat.po;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * @author Sharp
 */
@Data
public class WechatBaseRes {
    @JsonProperty("errcode")
    private Integer errCode;
    @JsonProperty("errmsg")
    private String errMsg;
}
