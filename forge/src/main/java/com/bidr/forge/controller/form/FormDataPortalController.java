package com.bidr.forge.controller.form;

import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.service.form.FormDataPortalService;
import com.bidr.forge.vo.form.FormDataVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单填写数据表管理控制器
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单数据 - 填写数据")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/data"})
public class FormDataPortalController extends BaseAdminController<FormData, FormDataVO> {

    private final FormDataPortalService formDataPortalService;

    @Override
    public PortalCommonService<FormData, FormDataVO> getPortalService() {
        return formDataPortalService;
    }
}
