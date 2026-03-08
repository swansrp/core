package com.bidr.forge.controller.form;

import com.bidr.forge.dao.entity.FormDataGroupInstance;
import com.bidr.forge.service.form.FormDataGroupInstancePortalService;
import com.bidr.forge.vo.form.FormDataGroupInstanceVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 属性分组实例表管理控制器
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单数据 - 分组实例")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/group/instance"})
public class FormDataGroupInstancePortalController extends BaseAdminController<FormDataGroupInstance, FormDataGroupInstanceVO> {

    private final FormDataGroupInstancePortalService formDataGroupInstancePortalService;

    @Override
    public PortalCommonService<FormDataGroupInstance, FormDataGroupInstanceVO> getPortalService() {
        return formDataGroupInstancePortalService;
    }
}
