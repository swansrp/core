package com.bidr.forge.controller;

import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.bidr.forge.dao.entity.SysFormConfig;
import com.bidr.forge.service.SysFormConfigPortalService;
import com.bidr.forge.vo.SysFormConfigVO;
import com.bidr.kernel.controller.BaseAdminOrderController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动态表单配置Portal Controller
 *
 * @author sharp
 * @since 2025-11-20
 */
@Api(tags = "动态配置 - 动态表单配置")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/forge/form-config"})
public class SysFormConfigPortalController extends BaseAdminOrderController<SysFormConfig, SysFormConfigVO> {

    private final SysFormConfigPortalService sysFormConfigPortalService;

    @Override
    public PortalCommonService<SysFormConfig, SysFormConfigVO> getPortalService() {
        return sysFormConfigPortalService;
    }

    @Override
    protected SFunction<SysFormConfig, ?> id() {
        return SysFormConfig::getId;
    }

    @Override
    protected SFunction<SysFormConfig, Integer> order() {
        return SysFormConfig::getSort;
    }
}
