package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
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
     *
     * @param keyword 模糊搜索关键字（匹配dictName或dictCode），可为null
     */
    public List<SysDynamicDictConfig> getAllValidConfigs(String keyword) {
        LambdaQueryWrapper<SysDynamicDictConfig> wrapper = super.getQueryWrapper();
        wrapper.eq(SysDynamicDictConfig::getValid, CommonConst.YES);
        wrapper.and(FuncUtil.isNotEmpty(keyword), w -> w
                .like(SysDynamicDictConfig::getDictName, keyword)
                .or()
                .like(SysDynamicDictConfig::getDictCode, keyword));
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

    /**
     * 根据字典编码获取配置（包含已软删除的记录）
     * <p>
     * 用于 saveOrUpdate 查重：dict_code 有唯一键 uk_dict_code，若仅查 valid=1 会遗漏软删除记录，
     * 导致新增时撞唯一键。此方法不过滤 valid，确保能复用软删除记录。
     */
    public SysDynamicDictConfig getByDictCodeIncludeInvalid(String dictCode) {
        LambdaQueryWrapper<SysDynamicDictConfig> wrapper = super.getQueryWrapper();
        wrapper.eq(SysDynamicDictConfig::getDictCode, dictCode);
        return super.getOne(wrapper);
    }
}
