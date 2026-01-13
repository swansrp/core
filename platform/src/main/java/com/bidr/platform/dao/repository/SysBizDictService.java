package com.bidr.platform.dao.repository;

import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.mapper.SysBizDictDao;
import org.springframework.stereotype.Service;

/**
 * 字典表 Repository Service
 *
 * @author sharp
 */
@Service
public class SysBizDictService extends BaseSqlRepo<SysBizDictDao, SysBizDict> {
    // 仅包含业务逻辑方法
}
