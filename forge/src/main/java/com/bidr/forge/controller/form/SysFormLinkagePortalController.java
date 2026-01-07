package com.bidr.forge.controller.form;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysFormLinkage;
import com.bidr.forge.service.form.SysFormLinkagePortalService;
import com.bidr.forge.vo.form.SysFormLinkageVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 表单项联动配置Portal Controller
 *
 * @author sharp
 * @since 2025-11-20
 */
@Api(tags = "系统基础 - 动态表单 - 表单联动配置")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/form-linkage"})
public class SysFormLinkagePortalController extends BaseAdminOrderController<SysFormLinkage, SysFormLinkageVO> {

    private final SysFormLinkagePortalService sysFormLinkagePortalService;

    @Override
    public PortalCommonService<SysFormLinkage, SysFormLinkageVO> getPortalService() {
        return sysFormLinkagePortalService;
    }

    @Override
    protected SFunction<SysFormLinkage, ?> id() {
        return SysFormLinkage::getId;
    }

    @Override
    protected SFunction<SysFormLinkage, Integer> order() {
        return SysFormLinkage::getSort;
    }

    /**
     * 执行表单联动
     *
     * @param formConfigId 表单配置ID
     * @param formData     表单数据
     * @return 联动执行结果
     */
    @ApiOperation("执行表单联动")
    @PostMapping("/execute")
    public Map<String, Object> executeLinkage(@RequestParam Long formConfigId, @RequestBody Map<String, Object> formData) {
        return sysFormLinkagePortalService.executeLinkage(formConfigId, formData);
    }
}
