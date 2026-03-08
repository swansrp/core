package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormDataSectionInstance;
import com.bidr.forge.dao.mapper.FormDataSectionInstanceMapper;
import org.springframework.stereotype.Service;

/**
 * 表单区块实例 Repository Service
 *
 * @author sharp
 */
@Service
public class FormDataSectionInstanceService extends BaseSqlRepo<FormDataSectionInstanceMapper, FormDataSectionInstance> {
}
