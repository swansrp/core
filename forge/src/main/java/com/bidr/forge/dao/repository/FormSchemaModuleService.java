package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormSchemaModule;
import com.bidr.forge.dao.mapper.FormSchemaModuleMapper;
import org.springframework.stereotype.Service;

/**
 * 表单模块 Repository Service
 *
 * @author sharp
 */
@Service
public class FormSchemaModuleService extends BaseSqlRepo<FormSchemaModuleMapper, FormSchemaModule> {
}
