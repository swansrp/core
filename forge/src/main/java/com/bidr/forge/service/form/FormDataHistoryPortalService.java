package com.bidr.forge.service.form;

import com.bidr.admin.service.common.BasePortalService;
import com.bidr.authorization.holder.AccountContext;
import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.forge.vo.form.FormDataHistoryVO;
import com.bidr.kernel.constant.dict.common.ApprovalDict;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 表单填写历史 Portal Service
 *
 * @author sharp
 */
@Service
@RequiredArgsConstructor
public class FormDataHistoryPortalService extends BasePortalService<FormDataHistory, FormDataHistoryVO> {

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
}
