package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.forge.service.form.FormSchemaAttributePortalService;
import com.bidr.forge.vo.form.FormSchemaAttributeVO;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单字段属性管理控制器（支持排序）
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单配置 - 字段属性")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/attribute"})
public class FormSchemaAttributePortalController extends BaseAdminOrderController<FormSchemaAttribute, FormSchemaAttributeVO> {

    private final FormSchemaAttributePortalService formSchemaAttributePortalService;

    @Override
    public PortalCommonService<FormSchemaAttribute, FormSchemaAttributeVO> getPortalService() {
        return formSchemaAttributePortalService;
    }

    @Override
    protected SFunction<FormSchemaAttribute, ?> id() {
        return FormSchemaAttribute::getId;
    }

    @Override
    protected SFunction<FormSchemaAttribute, Integer> order() {
        return FormSchemaAttribute::getSort;
    }
}
