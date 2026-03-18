package com.bidr.forge.controller.form;

import com.bidr.authorization.holder.AccountContext;
import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.forge.service.form.FormDataHistoryPortalService;
import com.bidr.forge.vo.form.FormDataHistoryVO;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.controller.BaseAdminController;
import com.bidr.kernel.service.PortalCommonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * 提交产品填报
     * - 将上报历史记录的状态从草稿(0)变更为提交(1)
     * - 记录提交人和提交时间
     *
     * @param historyId 上报历史记录ID
     */
    @ApiOperation("提交表单填报")
    @PostMapping("/submit")
    public void submit(@RequestParam String historyId) {
        formDataHistoryPortalService.submit(historyId);
        Resp.notice("表单填报已提交，等待审核");
    }

    /**
     * 审批通过表单填写历史
     * - 将上报历史记录的状态从提交(1)变更为通过(3)
     * - 记录审批人和审批时间
     *
     * @param historyId 上报历史记录ID
     */
    @ApiOperation("审批通过表单填写历史")
    @PostMapping("/approve")
    public void approve(
            @ApiParam(value = "上报历史记录ID", required = true) @RequestParam String historyId) {
        formDataHistoryPortalService.approve(historyId);
        Resp.notice("表单填写历史审批通过");
    }

    /**
     * 拒绝表单填写历史
     * - 将上报历史记录的状态从提交(1)变更为退回(2)
     * - 记录审批人和审批时间
     *
     * @param historyId    上报历史记录ID
     * @param rejectReason 拒绝原因
     */
    @ApiOperation("拒绝表单填写历史")
    @PostMapping("/reject")
    public void reject(
            @ApiParam(value = "上报历史记录ID", required = true) @RequestParam String historyId,
            @ApiParam(value = "拒绝原因") @RequestParam(required = false) String rejectReason) {
        formDataHistoryPortalService.reject(historyId, rejectReason);
        Resp.notice("表单填写历史已拒绝");
    }
}
