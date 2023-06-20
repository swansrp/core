package com.bidr.sms.constant.dict;

import cn.hutool.core.util.EnumUtil;
import com.bidr.kernel.constant.dict.Dict;
import com.bidr.kernel.constant.dict.MetaDict;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: AliMessageTemplateConfirmStatusDict
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/29 09:37
 */
@Getter
@AllArgsConstructor
@MetaDict(value = "ALI_SMS_TEMP_CONFIRM_STATUS_DICT", remark = "短信模板审批状态")
public enum AliMessageTemplateConfirmStatusDict implements Dict {
    /**
     * 短信模板审批状态
     */

    AUDIT_STATE_INIT(0, "审批中"),
    AUDIT_STATE_PASS(1, "审批通过"),
    AUDIT_STATE_NOT_PASS(2, "审批拒绝"),

    AUDIT_STATE_CANCEL(10, "取消审核");

    private final Integer value;
    private final String label;

    public static AliMessageTemplateConfirmStatusDict of(Integer value) {
        return EnumUtil.getBy(AliMessageTemplateConfirmStatusDict::getValue, value);
    }

    public static AliMessageTemplateConfirmStatusDict of(String name) {
        return EnumUtil.getBy(AliMessageTemplateConfirmStatusDict::name, name);
    }
}
