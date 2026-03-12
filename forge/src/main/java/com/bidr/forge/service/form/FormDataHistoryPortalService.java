package com.bidr.forge.service.form;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.forge.dao.repository.FormDataHistoryService;
import com.bidr.forge.vo.form.FormDataHistoryVO;
import com.bidr.kernel.constant.dict.common.ApprovalDict;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.validate.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

/**
 * 表单填写历史 Portal Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class FormDataHistoryPortalService extends BasePortalService<FormDataHistory, FormDataHistoryVO> {

    private final FormDataHistoryService formDataHistoryService;

    @Override
    public void beforeAdd(FormDataHistory entity) {
        super.beforeAdd(entity);
        entity.setSubmittedBy(AccountContext.getOperator());
        entity.setSubmittedAt(new Date());
        entity.setStatus(ApprovalDict.UNKNOWN.getValue());
    }

    @Override
    public void afterAdd(FormDataHistory formDataHistory) {
        super.afterAdd(formDataHistory);
    }

    /**
     * 审批通过表单填写历史
     *
     * @param historyId 上报历史记录ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void approve(String historyId) {
        FormDataHistory history = formDataHistoryService.selectById(historyId);
        Validator.assertNotNull(history, ErrCodeSys.PA_DATA_NOT_EXIST, "上报记录");
        Validator.assertTrue(ApprovalDict.APPLY.getValue().equals(history.getStatus()),
                ErrCodeSys.SYS_ERR_MSG, "只有已提交状态的记录才能审批");

        history.setStatus(ApprovalDict.APPROVAL.getValue());
        history.setConfirmBy(AccountContext.getOperator());
        history.setConfirmAt(new Date());
        formDataHistoryService.updateById(history);
    }

    /**
     * 拒绝表单填写历史
     *
     * @param historyId    上报历史记录ID
     * @param rejectReason 拒绝原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void reject(String historyId, String rejectReason) {
        FormDataHistory history = formDataHistoryService.selectById(historyId);
        Validator.assertNotNull(history, ErrCodeSys.PA_DATA_NOT_EXIST, "上报记录");
        Validator.assertTrue(ApprovalDict.APPLY.getValue().equals(history.getStatus()),
                ErrCodeSys.SYS_ERR_MSG, "只有已提交状态的记录才能审批");

        history.setStatus(ApprovalDict.REJECT.getValue());
        history.setConfirmBy(AccountContext.getOperator());
        history.setConfirmAt(new Date());
        if (rejectReason != null && !rejectReason.trim().isEmpty()) {
            history.setConfirmReason(rejectReason);
        }
        formDataHistoryService.updateById(history);
    }
}
