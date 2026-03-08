package com.bidr.forge.controller.form;

import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.forge.service.form.FormDataHistoryPortalService;
import com.bidr.forge.vo.form.FormDataHistoryVO;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 表单填写历史管理控制器
 *
 * @author sharp
 */
@Api(tags = "系统基础 - 表单数据 - 填写历史")
@RestController
@RequiredArgsConstructor
@RequestMapping(path = {"/web/form/history"})
public class FormDataHistoryPortalController extends BaseAdminController<FormDataHistory, FormDataHistoryVO> {

    private final FormDataHistoryPortalService formDataHistoryPortalService;

    @Override
    public PortalCommonService<FormDataHistory, FormDataHistoryVO> getPortalService() {
        return formDataHistoryPortalService;
    }
}
