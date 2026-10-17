package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.FormSchemaModule;
import com.bidr.forge.service.form.FormSchemaModulePortalService;
import com.bidr.forge.vo.form.FormSchemaModuleVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单模块管理控制器（支持排序）
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单配置 - 表单模块")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/module"})
public class FormSchemaModulePortalController extends BaseAdminOrderController<FormSchemaModule, FormSchemaModuleVO> {

    private final FormSchemaModulePortalService formSchemaModulePortalService;

    @Override
    public PortalCommonService<FormSchemaModule, FormSchemaModuleVO> getPortalService() {
        return formSchemaModulePortalService;
    }

    @Override
    protected SFunction<FormSchemaModule, ?> id() {
        return FormSchemaModule::getId;
    }

    @Override
    protected SFunction<FormSchemaModule, Integer> order() {
        return FormSchemaModule::getSort;
    }
}
