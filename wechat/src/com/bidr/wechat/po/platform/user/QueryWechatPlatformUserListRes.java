/**
 * Title: QueryWechatPlatformUserListRes
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 * @date 2019-5-21 0:12
 * @description Project Name: Tanya
 * Package: com.srct.service.wechat.po.platform
 */
package com.bidr.wechat.po.platform.user;

import com.bidr.wechat.po.WechatBaseRes;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class QueryWechatPlatformUserListRes extends WechatBaseRes {
    private Integer total;
    private Integer count;
    private OpenId data;
    @JsonProperty("next_openid")
    private String nextOpenId;

}
