package com.bidr.sms.service;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.dao.repository.SaSmsTemplateService;
import com.bidr.sms.service.message.SmsManageService;
import com.bidr.sms.vo.ApplySmsTemplateReq;
import com.bidr.sms.vo.SmsTemplateCodeRes;
import com.bidr.sms.vo.SmsTemplateRes;
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
        return Resp.convert(saSmsTemplateService.getTemplateByPlatform(platform), SmsTemplateRes.class);
    }

    public SmsTemplateCodeRes addTemplate(ApplySmsTemplateReq req) {
        return smsManageService.applySmsTemplate(req);
    }

    public SmsTemplateCodeRes updateTemplate(SaSmsTemplate req) {
        return smsManageService.updateSmsTemplate(req);
    }

    public SmsTemplateCodeRes deleteTemplate(String id) {
        return smsManageService.deleteSmsTemplate(id);
    }

    public void syncTemplateConfirmStatus() {
        smsManageService.syncTemplateConfirmStatus();
    }
}
