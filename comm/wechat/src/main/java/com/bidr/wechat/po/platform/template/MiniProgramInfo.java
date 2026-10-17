/**
 * Title: MiniProgramInfo
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 * @date 2019-5-20 22:25
 * @description Project Name: Tanya
 * Package: com.srct.service.wechat.po.platform
 */
package com.bidr.wechat.po.platform.template;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class MiniProgramInfo {

    private String appId;
    @JsonProperty("pagepath")
    private String pagePath;
}
