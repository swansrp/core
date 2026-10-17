/**
 * Title: WechatMsgTemplateKey
 * Description: Copyright: Copyright (c) 2019 Company: Sharp
 *
 * @author Sharp
 * @date 2019-5-20 22:26
 * @description Project Name: Tanya
 * Package: com.srct.service.wechat.po.platform
 */
package com.bidr.wechat.po.platform.template;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class WechatMsgTemplateKey {
    private WechatMsgTemplateWord first;
    private WechatMsgTemplateWord keyword1;
    private WechatMsgTemplateWord keyword2;
    private WechatMsgTemplateWord keyword3;
    private WechatMsgTemplateWord keyword4;
    private WechatMsgTemplateWord keyword5;
    private WechatMsgTemplateWord remark;
}
