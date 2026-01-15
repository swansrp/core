package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
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

    public SysBizDict getDictByCode(String dictCode, String value) {
        LambdaQueryWrapper<SysBizDict> wrapper = super.getQueryWrapper();
        wrapper.eq(SysBizDict::getDictCode, dictCode);
        wrapper.eq(SysBizDict::getValue, value);
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        return super.getOne(wrapper);
    }

    public void updateDictName(String dictCode, String dictName) {
        LambdaQueryWrapper<SysBizDict> wrapper = super.getQueryWrapper();
        wrapper.eq(SysBizDict::getDictCode, dictCode);

        SysBizDict entity = new SysBizDict();
        entity.setDictName(dictName);
        super.update(entity, wrapper);
    }
    // 仅包含业务逻辑方法
}
