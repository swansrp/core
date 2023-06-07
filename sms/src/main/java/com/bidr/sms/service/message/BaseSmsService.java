package com.bidr.sms.service.message;

import com.bidr.authorization.constants.token.TokenItem;
import com.bidr.authorization.service.token.TokenService;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.sms.constant.message.SendMessageStatusEnum;
import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.dao.repository.SaSmsSendService;
import com.bidr.sms.dao.repository.SaSmsTemplateService;
import com.bidr.sms.service.message.cache.SmsTemplateCacheService;
import com.bidr.sms.vo.SendSmsReq;
import com.bidr.sms.vo.SendSmsRes;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Map;

/**
 * Title: BaseSmsService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 10:49
 */
public abstract class BaseSmsService implements SmsService {

    @Resource
    private SmsTemplateCacheService smsTemplateCacheService;
    @Resource
    private SaSmsSendService saSmsSendService;

    @Resource
    private SaSmsTemplateService saSmsTemplateService;
    @Resource
    private WebApplicationContext webApplicationContext;
    @Resource
    private TokenService tokenService;

    @Override
    public SendSmsRes sendSms(SendSmsReq sendSmsReq) {
        SendSmsRes res = new SendSmsRes();
        send(sendSmsReq, res);
        return res;
    }

    @Override
    public SendSmsRes sendAsyncSms(SendSmsReq sendSmsReq) {
        SendSmsRes res = new SendSmsRes();
        try {
            SaSmsSend saSmsSend = webApplicationContext.getBean(this.getClass()).initSendSms(sendSmsReq);
            res.setRequestId(saSmsSend.getSendId());
            res.setBizId(sendSmsReq.getBizId());
            webApplicationContext.getBean(this.getClass()).send(sendSmsReq, res);
        } catch (ServiceException e) {
            res.setCode(e.getErrCode().getErrCode());
            res.setMessage(e.getErrCode().getErrText());
        }
        return res;
    }

    @Override
    public SendSmsRes getSendSmsRes(String requestId) {
        SaSmsSend smsSend = saSmsSendService.getSaSmsSendByRequestId(requestId);
        return Resp.convert(smsSend, SendSmsRes.class);
    }

    @Async
    public void send(SendSmsReq sendSmsReq, SendSmsRes res) {
        SaSmsSend saSmsSend = webApplicationContext.getBean(this.getClass()).initSendSms(sendSmsReq);
        res.setRequestId(saSmsSend.getSendId());
        if (StringUtil.convertSwitch(sendSmsReq.getMock())) {
            saveMockSendSms(saSmsSend);
        } else {
            sendMessage(saSmsSend, res);
            saveSendSms(saSmsSend);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public SaSmsSend initSendSms(SendSmsReq sendSmsReq) {
        SaSmsSend saSmsSend = buildSmsSend(sendSmsReq);
        saSmsSend.setSendStatus(SendMessageStatusEnum.REQUEST.getStatus());
        saSmsSend.setSendResult(SendMessageStatusEnum.REQUEST.getResult());
        String platform = tokenService.getItem(TokenItem.PLATFORM.name(), String.class);
        saSmsSend.setPlatform(platform);
        saSmsSendService.insert(saSmsSend);
        return saSmsSend;
    }

    protected SendSmsRes saveMockSendSms(SaSmsSend smsSend) {
        smsSend.setSendStatus(SendMessageStatusEnum.MOCK.getStatus());
        smsSend.setSendResult(SendMessageStatusEnum.MOCK.getResult());
        saSmsSendService.updateById(smsSend);
        return buildSendSmsRes(smsSend);
    }

    /**
     * 发送短信
     *
     * @param request
     * @return
     */
    protected abstract void sendMessage(SaSmsSend request, SendSmsRes res);

    protected void saveSendSms(SaSmsSend smsSend) {
        saSmsSendService.updateById(smsSend);
    }

    protected SaSmsSend buildSmsSend(SendSmsReq sendSmsReq) {
        String phoneNumber = sendSmsReq.getPhoneNumbers();
        SaSmsTemplate saSmsTemplate = null;
        if (FuncUtil.isNotEmpty(sendSmsReq.getTemplateCode())) {
            saSmsTemplate = saSmsTemplateService.selectOneByTemplateCode(sendSmsReq.getTemplateCode());
            if (FuncUtil.isNotEmpty(saSmsTemplate)) {
                sendSmsReq.setSendSmsType(saSmsTemplate.getSmsType());
            }
        }
        if (FuncUtil.isEmpty(saSmsTemplate)) {
            saSmsTemplate = smsTemplateCacheService.getCache(sendSmsReq.getSendSmsType());
        }
        Validator.assertNotNull(saSmsTemplate, ErrCodeSys.PA_DATA_NOT_EXIST, "短信模板");
        validateProperty(saSmsTemplate, sendSmsReq.getParamMap());
        String templateCode = saSmsTemplate.getTemplateCode();
        SaSmsSend smsSend = new SaSmsSend();
        smsSend.setMobile(phoneNumber);
        if (FuncUtil.isNotEmpty(sendSmsReq.getParamMap())) {
            smsSend.setSendParam(JsonUtil.toJson(sendSmsReq.getParamMap()));
        }
        smsSend.setSendSign(saSmsTemplate.getSign());
        smsSend.setBizId(sendSmsReq.getBizId());
        smsSend.setPlatform(saSmsTemplate.getPlatform());
        smsSend.setTemplateCode(templateCode);
        smsSend.setSendId(saSmsTemplate.getSign());
        smsSend.setSendType(sendSmsReq.getSendSmsType());
        smsSend.setSendAt(new Date());
        return smsSend;
    }

    private SendSmsRes buildSendSmsRes(SaSmsSend smsSend) {
        SendSmsRes res = new SendSmsRes();
        res.setRequestId(smsSend.getSendId());
        res.setCode(smsSend.getSendStatus());
        res.setMessage(smsSend.getSendResult());
        return res;
    }

    private void validateProperty(SaSmsTemplate saSmsTemplate, Map<String, String> paramMap) {
        if (FuncUtil.isNotEmpty(saSmsTemplate.getParameter())) {
            Map<String, String> map = JsonUtil.readJson(saSmsTemplate.getParameter(), Map.class, String.class,
                    String.class);
            if (FuncUtil.isNotEmpty(map)) {
                Validator.assertTrue(FuncUtil.isNotEmpty(paramMap), ErrCodeSys.PA_PARAM_FORMAT, "短信参数");
                for (Map.Entry<String, String> entry : map.entrySet()) {
                    Validator.assertNotNull(paramMap.get(entry.getKey()), ErrCodeSys.PA_PARAM_FORMAT, "短信参数错误");
                }
            }
        }
    }
}
