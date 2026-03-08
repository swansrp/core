package com.bidr.forge.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.forge.dao.entity.FormData;
import com.bidr.forge.dao.mapper.FormDataMapper;
import org.springframework.stereotype.Service;

/**
 * 表单填写数据表 Repository Service
 *
 * @author sharp
 */
@Service
public class FormDataService extends BaseSqlRepo<FormDataMapper, FormData> {
}
