package com.bidr.sms.controller;

import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.kernel.vo.common.IdReqVO;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.service.AdminSmsTemplateService;
import com.bidr.sms.vo.ApplySmsTemplateReq;
import com.bidr.sms.vo.SmsTemplateCodeRes;
import com.bidr.sms.vo.SmsTemplateRes;
import com.bidr.sms.vo.UpdateSmsTemplateReq;
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
@RequestMapping(value = "/web/sms/template/admin")
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
    public SmsTemplateCodeRes addTemplate(@RequestBody ApplySmsTemplateReq req) {
        return adminSmsTemplateService.addTemplate(req);
    }

    @ApiOperation(value = "同步短信模板审核状态")
    @RequestMapping(value = "/sync", method = RequestMethod.POST)
    public void syncTemplate() {
        adminSmsTemplateService.syncTemplate();
        Resp.notice("同步短信模板审核状态成功");
    }

    @Override
    protected void preUpdate(SaSmsTemplate saSmsTemplate) {
        adminSmsTemplateService.updateTemplate(saSmsTemplate.getTemplateCode(), saSmsTemplate.getBody());
        Resp.notice("修改短信模板成功, 等待审批");
    }

    @Override
    protected void preDelete(IdReqVO vo) {
        adminSmsTemplateService.deleteTemplate(vo.getId());
    }
}
