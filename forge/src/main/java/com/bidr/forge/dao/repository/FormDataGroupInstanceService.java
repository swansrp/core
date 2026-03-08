package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormDataGroupInstance;
import com.bidr.forge.dao.mapper.FormDataGroupInstanceMapper;
import org.springframework.stereotype.Service;

/**
 * 属性分组实例表 Repository Service
 *
 * @author sharp
 */
@Service
public class FormDataGroupInstanceService extends BaseSqlRepo<FormDataGroupInstanceMapper, FormDataGroupInstance> {
}
