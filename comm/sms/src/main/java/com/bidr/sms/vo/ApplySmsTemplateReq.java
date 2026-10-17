package com.bidr.sms.vo;

import com.bidr.sms.constant.dict.AliMessageTemplateTypeDict;
import lombok.Data;

/**
 * Title: ApplySmsTempalteReq
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 15:56
 */
@Data
public class ApplySmsTemplateReq {
    private String templateTitle;
    private AliMessageTemplateTypeDict templateType;
    private String smsType;
    private String body;
    private String sign;
    private String remark;
    private String platform;
}
