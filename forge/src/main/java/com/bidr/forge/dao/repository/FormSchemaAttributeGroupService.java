package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormSchemaAttributeGroup;
import com.bidr.forge.dao.mapper.FormSchemaAttributeGroupMapper;
import org.springframework.stereotype.Service;

/**
 * 表单字段分组 Repository Service
 *
 * @author sharp
 */
@Service
public class FormSchemaAttributeGroupService extends BaseSqlRepo<FormSchemaAttributeGroupMapper, FormSchemaAttributeGroup> {
}
