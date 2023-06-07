package com.bidr.sms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.config.response.Resp;
import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.dao.repository.SaSmsSendService;
import com.bidr.sms.dao.repository.SaSmsTemplateService;
import com.bidr.sms.service.message.SmsService;
import com.bidr.sms.vo.SendSmsReq;
import com.bidr.sms.vo.SendSmsVO;
import com.bidr.sms.vo.SmsHistoryReq;
import com.bidr.sms.vo.SmsHistoryRes;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: AdminSmsSendService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 10:25
 */
@Service
public class AdminSmsSendService {

    @Resource
    private SaSmsSendService saSmsSendService;
    @Resource
    private SmsService smsService;
    @Resource
    private SaSmsTemplateService saSmsTemplateService;

    public void sendSms(SendSmsVO req) {
        SendSmsReq sendSmsReq = buildSendSmsReq(req);
        smsService.sendSms(sendSmsReq);
    }

    private SendSmsReq buildSendSmsReq(SendSmsVO req) {
        SendSmsReq sendSmsReq = new SendSmsReq();
        SaSmsTemplate saSmsTemplate = saSmsTemplateService.selectOneByTemplateCode(req.getTemplateCode());
        sendSmsReq.setSendSmsType(saSmsTemplate.getSmsType());
        sendSmsReq.setParamMap(req.getProp());
        sendSmsReq.setPhoneNumbers(req.getPhoneNumber());
        return sendSmsReq;
    }

    public Page<SmsHistoryRes> getSmsHistory(SmsHistoryReq req) {
        Page<SaSmsSend> saSmsSendList = saSmsSendService.getSaSmsSend(req);
        return Resp.convert(saSmsSendList, SmsHistoryRes.class);
    }
}
