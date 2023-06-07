package com.bidr.sms.constant.param;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.constant.param.MetaParam;
import com.bidr.kernel.constant.param.Param;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Title: SmsParam
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 09:24
 */
@AllArgsConstructor
@Getter
@MetaParam
public enum SmsParam implements Param {
    /**
     * 短信参数
     */
    SMS_MOCK_MODE("短信模拟模式", CommonConst.YES, "");

    private final String title;
    private final String defaultValue;
    private final String remark;
}
