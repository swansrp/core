package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormSchemaAttribute;
import com.bidr.forge.dao.mapper.FormSchemaAttributeMapper;
import org.springframework.stereotype.Service;

/**
 * 表单字段属性 Repository Service
 *
 * @author sharp
 */
@Service
public class FormSchemaAttributeService extends BaseSqlRepo<FormSchemaAttributeMapper, FormSchemaAttribute> {
}
