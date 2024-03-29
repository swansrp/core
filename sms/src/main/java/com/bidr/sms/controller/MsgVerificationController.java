/**
 * Title: MsgVerificationController.java
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2019-8-1 23:36
 * @description Project Name: Grote
 * @Package: com.srct.service.controller
 */
package com.bidr.sms.controller;

import com.bidr.authorization.annotation.auth.Auth;
import com.bidr.authorization.annotation.auth.AuthToken;
import com.bidr.authorization.annotation.captcha.CaptchaVerify;
import com.bidr.authorization.config.msg.IMsgVerification;
import com.bidr.authorization.service.sms.MsgVerificationService;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.validate.Validator;
import com.bidr.sms.bo.SmsReq;
import com.bidr.sms.service.message.SmsService;
import com.bidr.sms.vo.SendSmsRes;
import com.bidr.sms.vo.internal.MsgCodeReq;
import com.bidr.sms.vo.internal.MsgCodeRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Api(tags = "系统基础 - 短信验证码")
@RestController("MsgVerificationController")
@RequestMapping(value = "/web/sms")
public class MsgVerificationController {

    @Resource
    private MsgVerificationService msgVerificationService;
    @Resource
    private SmsService smsService;

    @Auth(AuthToken.class)
    @ApiOperation(value = "获取短信验证吗", notes = "利用token获取短信验证码")
    @RequestMapping(value = "", method = RequestMethod.GET)
    @ApiImplicitParams({@ApiImplicitParam(paramType = "query", dataType = "String", name = "phoneNumber", value =
            "电话号码", required = true), @ApiImplicitParam(paramType = "query", dataType = "String", name = "msgCodeType"
            , required = true), @ApiImplicitParam(paramType = "query", dataType = "String", name = "graphCode",
            value = "图形验证码", required = true)})
    @CaptchaVerify(value = "", field = "msgCodeType")
    public MsgCodeRes getMsgCode(MsgCodeReq req) {
        IMsgVerification msgCodeType = msgVerificationService.getMsgCodeType(req.getMsgCodeType());
        Validator.assertNotNull(msgCodeType, ErrCodeSys.SYS_CONFIG_NOT_EXIST, "验证码类型");
        String code = msgVerificationService.generateMsgCode(req.getPhoneNumber(), msgCodeType);
        SmsReq smsSendReq = buildSendSmsReq(req.getPhoneNumber(), msgCodeType, code);
        SendSmsRes sendSmsRes = smsService.sendSms(smsSendReq);
        MsgCodeRes res = ReflectionUtil.copy(sendSmsRes, MsgCodeRes.class);
        res.setInternal(msgCodeType.getInternal());
        res.setLength(msgCodeType.getLength());
        res.setTimeout(msgCodeType.getTimeout());
        return res;
    }

    private SmsReq buildSendSmsReq(String phoneNumber, IMsgVerification msgVerificationType, String code) {
        SmsReq req = new SmsReq();
        req.setPhoneNumbers(phoneNumber);
        req.setSendSmsType(msgVerificationType.name());
        Map<String, String> paramMap = new HashMap<>(1);
        paramMap.put("code", code);
        req.setParamMap(paramMap);
        return req;
    }
}
