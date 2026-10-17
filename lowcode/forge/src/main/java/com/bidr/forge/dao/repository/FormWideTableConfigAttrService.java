package com.bidr.forge.dao.repository;

import com.bidr.forge.dao.entity.FormWideTableConfigAttr;
import com.bidr.forge.dao.mapper.FormWideTableConfigAttrMapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

/**
 * 宽表字段配置 Repository Service
 *
 * @author sharp
 */
@Service
public class FormWideTableConfigAttrService extends BaseSqlRepo<FormWideTableConfigAttrMapper, FormWideTableConfigAttr> {
}
