package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.forge.dao.mapper.FormDataHistoryMapper;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 表单填写历史 Repository Service
 *
 * @author sharp
 */
@Service
public class FormDataHistoryService extends BaseSqlRepo<FormDataHistoryMapper, FormDataHistory> {

    public FormDataHistory createFormDataHistory(String formId, String status, String submittedBy, Date submittedAt) {
        FormDataHistory formDataHistory = new FormDataHistory();
        formDataHistory.setFormId(formId);
        formDataHistory.setStatus(status);
        formDataHistory.setSubmittedBy(submittedBy);
        formDataHistory.setSubmittedAt(submittedAt);
        super.insert(formDataHistory);
        return formDataHistory;
    }
}
