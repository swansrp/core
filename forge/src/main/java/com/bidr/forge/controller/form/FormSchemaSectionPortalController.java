package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.FormSchemaSection;
import com.bidr.forge.service.form.FormSchemaSectionPortalService;
import com.bidr.forge.vo.form.FormSchemaSectionVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单区块管理控制器（支持排序）
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单配置 - 表单区块")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/section"})
public class FormSchemaSectionPortalController extends BaseAdminOrderController<FormSchemaSection, FormSchemaSectionVO> {

    private final FormSchemaSectionPortalService formSchemaSectionPortalService;

    @Override
    public PortalCommonService<FormSchemaSection, FormSchemaSectionVO> getPortalService() {
        return formSchemaSectionPortalService;
    }

    @Override
    protected SFunction<FormSchemaSection, ?> id() {
        return FormSchemaSection::getId;
    }

    @Override
    protected SFunction<FormSchemaSection, Integer> order() {
        return FormSchemaSection::getSort;
    }
}
