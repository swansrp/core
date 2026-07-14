package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.platform.dao.entity.SysDynamicDictConfig;
import com.bidr.platform.dao.mapper.SysDynamicDictConfigDao;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 动态字典配置 Repository Service
 *
 * @author Sharp
 * @since 2026-07-14
 */
@Service
public class SysDynamicDictConfigService extends BaseSqlRepo<SysDynamicDictConfigDao, SysDynamicDictConfig> {

    /**
     * 获取所有有效的动态字典配置
     */
    public List<SysDynamicDictConfig> getAllValidConfigs() {
        LambdaQueryWrapper<SysDynamicDictConfig> wrapper = super.getQueryWrapper();
        wrapper.eq(SysDynamicDictConfig::getValid, CommonConst.YES);
        return super.list(wrapper);
    }

    /**
     * 根据字典编码获取配置
     */
    public SysDynamicDictConfig getByDictCode(String dictCode) {
        LambdaQueryWrapper<SysDynamicDictConfig> wrapper = super.getQueryWrapper();
        wrapper.eq(SysDynamicDictConfig::getDictCode, dictCode);
        wrapper.eq(SysDynamicDictConfig::getValid, CommonConst.YES);
        return super.getOne(wrapper);
    }
}
