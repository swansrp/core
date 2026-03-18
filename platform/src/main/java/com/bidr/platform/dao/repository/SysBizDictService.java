package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.dao.entity.SysBizDict;
import com.bidr.platform.dao.mapper.SysBizDictDao;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * 获取所有业务字典列表（按dictCode分组，返回不同的字典）
     */
    public List<SysBizDict> getBizDictListByTitle(String title) {
        LambdaQueryWrapper<SysBizDict> wrapper = super.getQueryWrapper();
        wrapper.select(SysBizDict::getDictCode, SysBizDict::getDictName);
        wrapper.eq(FuncUtil.isNotEmpty(title), SysBizDict::getDictName, title);
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        wrapper.groupBy(SysBizDict::getDictCode, SysBizDict::getDictName);
        return super.list(wrapper);
    }

    /**
     * 根据字典编码获取字典项列表
     */
    public List<SysBizDict> getBizDictItemsByCode(String dictCode) {
        LambdaQueryWrapper<SysBizDict> wrapper = super.getQueryWrapper();
        wrapper.isNull(SysBizDict::getBizId);
        wrapper.eq(SysBizDict::getDictCode, dictCode);
        wrapper.eq(SysBizDict::getValid, CommonConst.YES);
        wrapper.orderByAsc(SysBizDict::getSort);
        return super.list(wrapper);
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
