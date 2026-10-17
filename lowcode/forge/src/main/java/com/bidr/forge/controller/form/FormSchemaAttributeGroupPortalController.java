package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.FormSchemaAttributeGroup;
import com.bidr.forge.service.form.FormSchemaAttributeGroupPortalService;
import com.bidr.forge.vo.form.FormSchemaAttributeGroupVO;
import com.bidr.kernel.controller.BaseAdminTreeController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单字段分组管理控制器（树形结构）
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单配置 - 字段分组")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/attribute/group"})
public class FormSchemaAttributeGroupPortalController extends BaseAdminTreeController<FormSchemaAttributeGroup, FormSchemaAttributeGroupVO> {

    private final FormSchemaAttributeGroupPortalService formSchemaAttributeGroupPortalService;

    @Override
    public PortalCommonService<FormSchemaAttributeGroup, FormSchemaAttributeGroupVO> getPortalService() {
        return formSchemaAttributeGroupPortalService;
    }

    @Override
    protected SFunction<FormSchemaAttributeGroup, ?> id() {
        return FormSchemaAttributeGroup::getId;
    }

    @Override
    protected SFunction<FormSchemaAttributeGroup, Integer> order() {
        return FormSchemaAttributeGroup::getSort;
    }

    @Override
    protected SFunction<FormSchemaAttributeGroup, ?> pid() {
        return FormSchemaAttributeGroup::getPid;
    }

    @Override
    protected SFunction<FormSchemaAttributeGroup, String> name() {
        return FormSchemaAttributeGroup::getTitle;
    }
}
