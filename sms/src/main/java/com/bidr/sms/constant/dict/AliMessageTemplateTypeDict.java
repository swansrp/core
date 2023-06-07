package com.bidr.sms.constant.dict;

import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: AliMessageTemplateTypeDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 14:36
 */
@AllArgsConstructor
@Getter
@MetaDict(value = "ALI_SMS_TEMP_TYPE_DICT", remark = "阿里短信模板类型")
public enum AliMessageTemplateTypeDict implements Dict {
    /**
     * 阿里短信模板类型
     */
    CERTIFICATE_CODE(0, "验证码"),
    NOTIFICATION(1, "通知"),
    PROMOTE(2, "推广"),
    INTERNATIONAL(3, "港澳台国际短息");

    private final Integer value;
    private final String label;

}
