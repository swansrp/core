package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormSchemaSection;
import com.bidr.forge.dao.mapper.FormSchemaSectionMapper;
import org.springframework.stereotype.Service;

/**
 * 表单区块 Repository Service
 *
 * @author sharp
 */
@Service
public class FormSchemaSectionService extends BaseSqlRepo<FormSchemaSectionMapper, FormSchemaSection> {
}
