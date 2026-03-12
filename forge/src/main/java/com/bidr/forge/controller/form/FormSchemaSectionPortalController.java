package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.FormSchemaSection;
import com.bidr.forge.dao.entity.SysMatrix;
import com.bidr.forge.service.form.FormSchemaSectionPortalService;
import com.bidr.forge.vo.form.FormSchemaSectionVO;
import com.bidr.forge.vo.matrix.SysMatrixVO;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /**
     * 为区块创建数据表
     * <p>
     * 根据区块ID创建一个数据表（矩阵），表名格式为：formCode_section_sectionId
     * 表中包含 history_id 和 section_instance_id 两个联合主键字段
     *
     * @param req 包含区块ID的请求对象
     * @return 创建的矩阵信息
     */
    @ApiOperation("为区块创建数据表")
    @PostMapping("/createMatrix")
    public void createMatrix(@RequestBody SysMatrixVO req, Long sectionId) {
        SysMatrix matrix = formSchemaSectionPortalService.createMatrixForSection(req, sectionId);
        Resp.notice("矩阵创建成功", matrix);
    }
}
