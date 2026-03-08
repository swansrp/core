package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.FormSchema;
import com.bidr.forge.service.form.FormSchemaPortalService;
import com.bidr.forge.vo.form.FormSchemaVO;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单管理控制器（树形结构）
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单配置 - 表单管理")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/schema"})
public class FormSchemaPortalController extends BaseAdminTreeController<FormSchema, FormSchemaVO> {

    private final FormSchemaPortalService formSchemaPortalService;

    @Override
    public PortalCommonService<FormSchema, FormSchemaVO> getPortalService() {
        return formSchemaPortalService;
    }

    @Override
    protected SFunction<FormSchema, ?> id() {
        return FormSchema::getId;
    }

    @Override
    protected SFunction<FormSchema, Integer> order() {
        return FormSchema::getSort;
    }

    @Override
    protected SFunction<FormSchema, ?> pid() {
        return FormSchema::getPid;
    }

    @Override
    protected SFunction<FormSchema, String> name() {
        return FormSchema::getTitle;
    }
}
