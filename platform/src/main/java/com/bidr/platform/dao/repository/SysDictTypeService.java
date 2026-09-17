package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.mapper.SysDictTypeDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Title: SysDictTypeService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/29 13:23
 */
@Service
public class SysDictTypeService extends BaseSqlRepo<SysDictTypeDao, SysDictType> {

    public List<SysDictType> getNotReadOnlySysDictType() {
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper().ne(SysDictType::getReadOnly, CommonConst.YES);
        return super.select(wrapper);
    }

    /**
     * 取「只读但代码里已不再声明」的字典类型，用于开机清理残留。
     * <p>
     * codeDictNameSet 为空时必须返回空：一方面 MP 的 notIn 遇到空集合会生成非法 SQL，
     * 另一方面「代码一个字典都没扫到」通常是扫描异常，此时若退化成全表命中会误删所有内置字典。
     */
    public List<SysDictType> getReadOnlyNotIn(Collection<String> codeDictNameSet) {
        if (FuncUtil.isEmpty(codeDictNameSet)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .eq(SysDictType::getReadOnly, CommonConst.YES)
                .notIn(SysDictType::getDictName, codeDictNameSet);
        return super.select(wrapper);
    }

    public List<SysDictType> getSysDictByTitle(String title) {
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .like(StringUtils.isNotEmpty(title), SysDictType::getDictTitle, title);
        return super.select(wrapper);
    }

    public void deleteByDictNameList(List<String> dictNameList) {
        if (FuncUtil.isEmpty(dictNameList)) {
            return;
        }
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .in(SysDictType::getDictName, dictNameList);
        super.delete(wrapper);
    }

    public void deleteByDictName(String dictName) {
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .eq(FuncUtil.isNotEmpty(dictName), SysDictType::getDictName, dictName);
        super.delete(wrapper);
    }
}



