package com.bidr.forge.controller.form;

import com.bidr.forge.dao.entity.FormDataSectionInstance;
import com.bidr.forge.service.form.FormDataSectionInstancePortalService;
import com.bidr.forge.vo.form.FormDataSectionInstanceVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单区块实例管理控制器
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单数据 - 区块实例")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/section/instance"})
public class FormDataSectionInstancePortalController extends BaseAdminController<FormDataSectionInstance, FormDataSectionInstanceVO> {

    private final FormDataSectionInstancePortalService formDataSectionInstancePortalService;

    @Override
    public PortalCommonService<FormDataSectionInstance, FormDataSectionInstanceVO> getPortalService() {
        return formDataSectionInstancePortalService;
    }
}
