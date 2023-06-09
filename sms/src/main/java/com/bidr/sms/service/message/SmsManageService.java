package com.bidr.sms.service.message;

import com.aliyun.dysmsapi20170525.models.*;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.utils.JsonUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.sms.constant.dict.AliMessageTemplateConfirmStatusDict;
import com.bidr.sms.constant.err.SmsErrorCode;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.dao.repository.SaSmsTemplateService;
import com.bidr.sms.service.message.ali.AliSmsManageService;
import com.bidr.sms.vo.ApplySmsTemplateReq;
import com.bidr.sms.vo.SmsTemplateCodeRes;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Title: SmsManageService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 15:55
 */
@Service
public class SmsManageService {

    private static final String SIGN_AUDIT_STATUS_PASS = "AUDIT_STATE_PASS";

    private static final String PARAMETER_REGEX = "\\{(\\w*)\\}";

    @Resource
    private SaSmsTemplateService saSmsTemplateService;

    @Resource
    private AliSmsManageService aliSmsManageService;

    public SmsTemplateCodeRes applySmsTemplate(ApplySmsTemplateReq req) {
        SaSmsTemplate saSmsTemplate = validateAndConvertSaSmsTemplate(req);
        AddSmsTemplateResponseBody responseBody = aliSmsManageService.applySmsTemplate(saSmsTemplate);
        saSmsTemplate.setTemplateCode(responseBody.getTemplateCode());
        saSmsTemplateService.insert(saSmsTemplate);
        SmsTemplateCodeRes res = new SmsTemplateCodeRes();
        res.setTemplateCode(responseBody.getTemplateCode());
        return res;
    }

    private SaSmsTemplate validateAndConvertSaSmsTemplate(ApplySmsTemplateReq req) {
        SaSmsTemplate saSmsTemplate = ReflectionUtil.copy(req, SaSmsTemplate.class);
        saSmsTemplate.setTemplateType(req.getTemplateType().getValue());
        buildParameter(saSmsTemplate);
        boolean existedSmsType = saSmsTemplateService.existedSmsType(req.getSmsType());
        Validator.assertFalse(existedSmsType, SmsErrorCode.SMS_TYPE_ALREADY_EXISTED, req.getSmsType());
        QuerySmsSignResponseBody response = aliSmsManageService.getSmsSignStatus(req.getSign());
        Validator.assertNotNull(response.getSignStatus(), SmsErrorCode.SIGN_NOT_REGISTER, req.getSign());
        String signStatus = response.getSignStatus().toString();
        Validator.assertYes(signStatus, SmsErrorCode.SIGN_NOT_REGISTER, req.getSign());
        return saSmsTemplate;
    }

    private void buildParameter(SaSmsTemplate saSmsTemplate) {
        String body = saSmsTemplate.getBody();
        Matcher matcher = Pattern.compile(PARAMETER_REGEX).matcher(body);
        Map<String, Object> prop = new HashMap<>();
        while (matcher.find()) {
            String group = matcher.group(0);
            String param = group.substring(1, group.length() - 1);
            prop.put(param, StringUtil.EMPTY);
        }
        saSmsTemplate.setParameter(JsonUtil.toJson(prop, false, false, true));
    }

    public List<String> getSmsSignList() {
        List<String> signList = new ArrayList<>();
        QuerySmsSignListResponseBody response = aliSmsManageService.getSmsSignList();
        if (FuncUtil.isNotEmpty(response.getSmsSignList())) {
            for (QuerySmsSignListResponseBody.QuerySmsSignListResponseBodySmsSignList sign :
                    response.getSmsSignList()) {
                if (StringUtil.equals(sign.getAuditStatus(), SIGN_AUDIT_STATUS_PASS)) {
                    signList.add(sign.getSignName());
                }
            }
        }
        return signList;
    }

    public void syncSmsTemplate() {
        List<SaSmsTemplate> noConfirmTemplate = saSmsTemplateService.getNoConfirmTemplate();
        if (CollectionUtils.isNotEmpty(noConfirmTemplate)) {
            for (SaSmsTemplate saSmsTemplate : noConfirmTemplate) {
                QuerySmsTemplateResponseBody response = aliSmsManageService.getSmsTemplateStatus(saSmsTemplate);
                saSmsTemplate.setConfirmStatus(response.getTemplateStatus());
                if ((AliMessageTemplateConfirmStatusDict.PASS.getValue()).equals(saSmsTemplate.getConfirmStatus())) {
                    saSmsTemplate.setConfirmAt(new Date());
                }
            }
            saSmsTemplateService.updateBatchById(noConfirmTemplate);
        }
    }

    public SaSmsTemplate getSmsTemplate(String templateCode) {
        SaSmsTemplate saSmsTemplate = saSmsTemplateService.selectOneByTemplateCode(templateCode);
        if ((AliMessageTemplateConfirmStatusDict.PASS.getValue()).equals(saSmsTemplate.getConfirmStatus())) {
            return saSmsTemplate;
        }
        QuerySmsTemplateResponseBody response = aliSmsManageService.getSmsTemplateStatus(saSmsTemplate);
        saSmsTemplate.setConfirmStatus(response.getTemplateStatus());
        saSmsTemplate.setReason(response.getReason());
        if ((AliMessageTemplateConfirmStatusDict.PASS.getValue()).equals(saSmsTemplate.getConfirmStatus())) {
            saSmsTemplate.setConfirmAt(new Date());
        }
        saSmsTemplateService.updateById(saSmsTemplate);
        return saSmsTemplate;
    }

    public List<SaSmsTemplate> getByPlatform(String platform) {
        return saSmsTemplateService.getTemplateByPlatform(platform);
    }

    public SmsTemplateCodeRes updateSmsTemplate(SaSmsTemplate saSmsTemplate) {
        ModifySmsTemplateResponseBody responseBody = aliSmsManageService.modifySmsSignRequest(saSmsTemplate);
        saSmsTemplate.setTemplateCode(responseBody.getTemplateCode());
        saSmsTemplateService.updateById(saSmsTemplate);
        SmsTemplateCodeRes res = new SmsTemplateCodeRes();
        res.setTemplateCode(responseBody.getTemplateCode());
        return res;
    }

    public SmsTemplateCodeRes deleteSmsTemplate(String templateId) {
        SaSmsTemplate saSmsTemplate = saSmsTemplateService.selectById(templateId);
        DeleteSmsTemplateResponseBody responseBody = aliSmsManageService.deleteSmsTemplate(saSmsTemplate);
        SmsTemplateCodeRes res = new SmsTemplateCodeRes();
        res.setTemplateCode(responseBody.getTemplateCode());
        return res;
    }
}
