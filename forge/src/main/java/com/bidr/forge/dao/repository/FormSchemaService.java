package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormSchema;
import com.bidr.forge.dao.mapper.FormSchemaMapper;
import org.springframework.stereotype.Service;

/**
 * 表单 Repository Service
 *
 * @author sharp
 */
@Service
public class FormSchemaService extends BaseSqlRepo<FormSchemaMapper, FormSchema> {
}
