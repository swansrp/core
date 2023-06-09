package com.bidr.sms.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Title: ApplySmsTempalteReq
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 15:56
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UpdateSmsTemplateReq extends ApplySmsTemplateReq {
    private String templateCode;
}
