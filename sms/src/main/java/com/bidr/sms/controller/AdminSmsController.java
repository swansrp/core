package com.bidr.sms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.service.AdminSmsSendService;
import com.bidr.sms.vo.SendSmsVO;
import com.bidr.sms.vo.SmsHistoryReq;
import com.bidr.sms.vo.SmsHistoryRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * Title: AdminSmsController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/31 10:20
 */
@Api(tags = "系统管理 - 短信 - 发送管理")
@RestController("AdminSmsController")
@RequestMapping(value = "/web/admin/sms")
public class AdminSmsController extends BaseAdminController<SaSmsSend, SmsHistoryRes> {
    @Resource
    private AdminSmsSendService adminSmsSendService;

    @ApiOperation(value = "发送短信")
    @RequestMapping(value = "/send", method = RequestMethod.POST)
    public Boolean sendSms(@RequestBody SendSmsVO req) {
        adminSmsSendService.sendSms(req);
        return null;
    }

    @ApiOperation(value = "获取短信发送历史")
    @RequestMapping(value = "/history", method = RequestMethod.GET)
    public Page<SmsHistoryRes> sendSms(SmsHistoryReq req) {
        return adminSmsSendService.getSmsHistory(req);
    }
}
