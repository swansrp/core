package com.bidr.sms.controller;

import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.service.AdminSmsTemplateService;
import com.bidr.sms.vo.ApplySmsTemplateReq;
import com.bidr.sms.vo.ApplySmsTemplateRes;
import com.bidr.sms.vo.SmsTemplateRes;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * Title: AdminSmsTemplateController
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/28 15:04
 */
@Api(tags = "系统管理 - 短信 - 模板管理")
@RestController("AdminSmsTemplateController")
@RequestMapping(value = "/web/admin/sms/template")
public class AdminSmsTemplateController extends BaseAdminController<SaSmsTemplate, SmsTemplateRes> {

    @Resource
    private AdminSmsTemplateService adminSmsTemplateService;

    @ApiOperation(value = "获取短信模板")
    @RequestMapping(value = "", method = RequestMethod.GET)
    public List<SmsTemplateRes> getTemplate(String platform) {
        return adminSmsTemplateService.getSmsTemplate(platform);
    }

    @ApiOperation(value = "添加短信模板模板")
    @RequestMapping(value = "/add", method = RequestMethod.POST)
    public ApplySmsTemplateRes addTemplate(@RequestBody ApplySmsTemplateReq req) {
        return adminSmsTemplateService.addTemplate(req);
    }

    @ApiOperation(value = "同步短信模板审核状态")
    @RequestMapping(value = "/sync", method = RequestMethod.POST)
    public Boolean syncTemplate() {
        adminSmsTemplateService.syncTemplate();
        return null;
    }

}
