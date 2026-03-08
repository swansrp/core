package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormDataHistory;
import com.bidr.forge.dao.mapper.FormDataHistoryMapper;
import org.springframework.stereotype.Service;

/**
 * 表单填写历史 Repository Service
 *
 * @author sharp
 */
@Service
public class FormDataHistoryService extends BaseSqlRepo<FormDataHistoryMapper, FormDataHistory> {
}
