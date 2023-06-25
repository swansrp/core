package com.bidr.sms.service.message.ali;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import com.bidr.sms.constant.message.SendMessageStatusDict;
import com.bidr.sms.constant.param.SmsParam;
import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.service.message.BaseSmsService;
import com.bidr.sms.vo.SendSmsRes;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * Title: AliSmsService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 09:54
 */
@Service
@RequiredArgsConstructor
public class AliSmsService extends BaseSmsService {
    private static final Integer SUCCESS_CODE = 200;
    private final SysConfigCacheService sysConfigCacheService;
    @Value("${ali-sms.endpoint}")
    private String endpoint;
    @Value("${ali-sms.access-key-id}")
    private String accessKeyId;
    @Value("${ali-sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${ali-sms.proxy:}")
    private String proxy;

    @Override
    protected void sendMessage(SaSmsSend smsSend, SendSmsRes res) {
        SendSmsRequest request = new SendSmsRequest().setPhoneNumbers(smsSend.getMobile())
                .setSignName(smsSend.getSendSign()).setTemplateCode(smsSend.getTemplateCode())
                .setTemplateParam(smsSend.getSendParam());
        try {
            if (!sysConfigCacheService.getSysConfigBool(SmsParam.SMS_MOCK_MODE)) {
                SendSmsResponse response = createClient().sendSms(request);
                convertSuccess(smsSend, res, response);
            } else {
                convertMock(smsSend, res);
            }

        } catch (Exception e) {
            convertFailed(smsSend, res);
        }
    }

    private Client createClient() throws Exception {
        Config config = new com.aliyun.teaopenapi.models.Config().setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = endpoint;
        if (FuncUtil.isNotEmpty(proxy)) {
            config.setHttpProxy(proxy);
        }
        return new Client(config);
    }

    private void convertSuccess(SaSmsSend smsSend, SendSmsRes res, SendSmsResponse response) {
        if (!SUCCESS_CODE.equals(response.getStatusCode())) {
            convertFailed(smsSend, res);
        } else {
            smsSend.setResponseStatus(response.getStatusCode());
            smsSend.setResponseMsg(response.getBody().getMessage());
            smsSend.setResponseCode(response.getBody().getCode());
            smsSend.setResponseAt(new Date());
            convertSuccess(smsSend, res);
        }
    }

    private void convertMock(SaSmsSend smsSend, SendSmsRes res) {
        Integer code = SendMessageStatusDict.MOCK.getValue();
        String result = SendMessageStatusDict.MOCK.getLabel();
        smsSend.setSendStatus(code);
        smsSend.setSendResult(result);
        res.setMessage(result);
        res.setStatus(code);
    }

    private void convertFailed(SaSmsSend smsSend, SendSmsRes res) {
        Integer code = SendMessageStatusDict.FAIL.getValue();
        String result = SendMessageStatusDict.FAIL.getLabel();
        smsSend.setSendStatus(code);
        smsSend.setSendResult(result);
        res.setMessage(result);
        res.setStatus(code);
    }

    private void convertSuccess(SaSmsSend smsSend, SendSmsRes res) {
        Integer code = SendMessageStatusDict.SUCCESS.getValue();
        String result = SendMessageStatusDict.SUCCESS.getLabel();
        smsSend.setSendStatus(code);
        smsSend.setSendResult(result);
        res.setMessage(result);
        res.setStatus(code);
    }
}
