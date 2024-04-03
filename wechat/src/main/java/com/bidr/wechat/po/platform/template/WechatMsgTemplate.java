/**
 * Title: WecahtMsgTemplatePO
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 * @date 2019-5-20 22:21
 * @description Project Name: Tanya
 * Package: com.srct.service.po.wechat
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
public class WechatMsgTemplate {
    @JsonProperty("touser")
    private String toUser;
    @JsonProperty("template_id")
    private String templateId;
    private String url;
    @JsonProperty("miniprogram")
    private MiniProgramInfo miniProgram;
    private WechatMsgTemplateKey data;
}
