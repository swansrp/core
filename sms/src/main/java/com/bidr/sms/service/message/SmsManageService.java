package com.bidr.sms.service.message;

import com.aliyun.dysmsapi20170525.models.*;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.*;
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
import org.springframework.transaction.annotation.Transactional;

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
        Map<String, Object> prop = new LinkedHashMap<>();
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

    @Transactional(rollbackFor = Exception.class)
    public void syncTemplate(String defaultPlatform, String defaultSign) {
        long total = Long.MAX_VALUE;
        int pageSize = 10;
        for (int currentPage = 1; ((long) currentPage - 1) * pageSize < total; currentPage++) {
            QuerySmsTemplateListResponseBody responseBody = aliSmsManageService.querySmsTemplateList(currentPage,
                    pageSize);
            if (FuncUtil.isNotEmpty(responseBody.getSmsTemplateList())) {
                for (QuerySmsTemplateListResponseBody.QuerySmsTemplateListResponseBodySmsTemplateList smsTemplate :
                        responseBody.getSmsTemplateList()) {
                    SaSmsTemplate saSmsTemplate = saSmsTemplateService.selectOneByTemplateCode(
                            smsTemplate.getTemplateCode());
                    if (FuncUtil.isNotEmpty(saSmsTemplate)) {
                        saSmsTemplate = buildSaSmsTemplate(smsTemplate, saSmsTemplate);
                        saSmsTemplateService.updateById(saSmsTemplate);
                    } else {
                        saSmsTemplate = new SaSmsTemplate();
                        saSmsTemplate.setPlatform(defaultPlatform);
                        saSmsTemplate.setSign(defaultSign);
                        saSmsTemplate.setTemplateCode(smsTemplate.getTemplateCode());
                        saSmsTemplate = buildSaSmsTemplate(smsTemplate, saSmsTemplate);
                        saSmsTemplateService.insert(saSmsTemplate);
                    }
                }
            } else {
                break;
            }
            total = responseBody.getTotalCount();
        }
    }

    private SaSmsTemplate buildSaSmsTemplate(
            QuerySmsTemplateListResponseBody.QuerySmsTemplateListResponseBodySmsTemplateList smsTemplate,
            SaSmsTemplate saSmsTemplate) {
        AliMessageTemplateConfirmStatusDict dict = AliMessageTemplateConfirmStatusDict.of(smsTemplate.getAuditStatus());
        Validator.assertNotNull(dict, ErrCodeSys.SYS_ERR_MSG, "未知状态: " + smsTemplate.getAuditStatus());
        saSmsTemplate.setConfirmStatus(dict.getValue());
        saSmsTemplate.setTemplateCode(smsTemplate.getTemplateCode());
        saSmsTemplate.setTemplateType(smsTemplate.getTemplateType());
        saSmsTemplate.setTemplateTitle(smsTemplate.getTemplateName());
        saSmsTemplate.setBody(smsTemplate.getTemplateContent());
        Date confirmDate = DateUtil.formatDate(smsTemplate.getCreateDate(), DateUtil.DATE_TIME_NORMAL);
        saSmsTemplate.setConfirmAt(confirmDate);
        saSmsTemplate.setReason(smsTemplate.getReason().getRejectInfo());
        buildParameter(saSmsTemplate);
        return saSmsTemplate;
    }

    public SaSmsTemplate getSmsTemplate(String templateCode) {
        SaSmsTemplate saSmsTemplate = saSmsTemplateService.selectOneByTemplateCode(templateCode);
        if ((AliMessageTemplateConfirmStatusDict.AUDIT_STATE_PASS.getValue()).equals(
                saSmsTemplate.getConfirmStatus())) {
            return saSmsTemplate;
        }
        QuerySmsTemplateResponseBody response = aliSmsManageService.getSmsTemplateStatus(saSmsTemplate);
        saSmsTemplate.setConfirmStatus(response.getTemplateStatus());
        saSmsTemplate.setReason(response.getReason());
        if ((AliMessageTemplateConfirmStatusDict.AUDIT_STATE_PASS.getValue()).equals(
                saSmsTemplate.getConfirmStatus())) {
            saSmsTemplate.setConfirmAt(new Date());
        }
        saSmsTemplateService.updateById(saSmsTemplate);
        return saSmsTemplate;
    }

    public void syncTemplateConfirmStatus() {
        List<SaSmsTemplate> noConfirmTemplate = saSmsTemplateService.getNoConfirmTemplate();
        if (CollectionUtils.isNotEmpty(noConfirmTemplate)) {
            for (SaSmsTemplate saSmsTemplate : noConfirmTemplate) {
                QuerySmsTemplateResponseBody response = aliSmsManageService.getSmsTemplateStatus(saSmsTemplate);
                saSmsTemplate.setConfirmStatus(response.getTemplateStatus());
                if ((AliMessageTemplateConfirmStatusDict.AUDIT_STATE_PASS.getValue()).equals(
                        saSmsTemplate.getConfirmStatus())) {
                    saSmsTemplate.setConfirmAt(new Date());
                }
            }
            saSmsTemplateService.updateBatchById(noConfirmTemplate);
        }
    }

    public List<SaSmsTemplate> getByPlatform(String platform) {
        return saSmsTemplateService.getTemplateByPlatform(platform);
    }

    public SmsTemplateCodeRes updateSmsTemplate(SaSmsTemplate req) {
        SaSmsTemplate saSmsTemplate = getSmsTemplate(req.getTemplateCode());
        if (FuncUtil.isNotEmpty(req.getPlatform())) {
            saSmsTemplate.setPlatform(req.getPlatform());
        }
        if (FuncUtil.isNotEmpty(req.getSign())) {
            saSmsTemplate.setSign(req.getSign());
        }
        if (FuncUtil.notEquals(req.getBody(), saSmsTemplate.getBody())) {
            saSmsTemplate.setBody(req.getBody());
            ModifySmsTemplateResponseBody responseBody = aliSmsManageService.modifySmsSignRequest(saSmsTemplate);
            saSmsTemplate.setTemplateCode(responseBody.getTemplateCode());
        }
        saSmsTemplateService.updateById(saSmsTemplate);
        SmsTemplateCodeRes res = new SmsTemplateCodeRes();
        res.setTemplateCode(saSmsTemplate.getTemplateCode());
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
