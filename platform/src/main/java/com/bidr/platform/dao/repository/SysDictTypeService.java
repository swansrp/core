package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.mapper.SysDictTypeDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

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

    public List<SysDictType> getSysDictByTitle(String title) {
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .like(StringUtils.isNotEmpty(title), SysDictType::getDictTitle, title);
        return super.select(wrapper);
    }

    public void deleteByDictNameList(List<String> dictNameList) {
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .in(FuncUtil.isNotEmpty(dictNameList), SysDictType::getDictName, dictNameList);
        super.delete(wrapper);
    }

    public void deleteByDictName(String dictName) {
        LambdaQueryWrapper<SysDictType> wrapper = super.getQueryWrapper()
                .eq(FuncUtil.isNotEmpty(dictName), SysDictType::getDictName, dictName);
        super.delete(wrapper);
    }
}



