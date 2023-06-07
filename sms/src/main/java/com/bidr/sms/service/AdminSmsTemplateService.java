package com.bidr.sms.service;

import com.bidr.sms.dao.repository.SaSmsTemplateService;
import com.bidr.sms.service.message.SmsManageService;
import com.bidr.sms.vo.ApplySmsTemplateReq;
import com.bidr.sms.vo.ApplySmsTemplateRes;
import com.bidr.sms.vo.SmsTemplateRes;
import com.bidr.kernel.config.response.Resp;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminSmsTemplateService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 08:42
 */
@Service
public class AdminSmsTemplateService {

    @Resource
    private SaSmsTemplateService saSmsTemplateService;
    @Resource
    private SmsManageService smsManageService;

    public List<SmsTemplateRes> getSmsTemplate(String platform) {
        return Resp.convert(saSmsTemplateService.getTemplateByPlatform(platform),
                SmsTemplateRes.class);
    }

    public ApplySmsTemplateRes addTemplate(ApplySmsTemplateReq req) {
        return smsManageService.applySmsTemplate(req);
    }

    public void syncTemplate() {
        smsManageService.syncSmsTemplate();
    }
}
