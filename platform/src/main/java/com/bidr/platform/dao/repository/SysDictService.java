package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.mapper.SysDictDao;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysDictService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 11:40
 */
@Service
public class SysDictService extends BaseSqlRepo<SysDictDao, SysDict> {

    public List<SysDict> getSysDictCache() {
        LambdaQueryWrapper<SysDict> wrapper = super.getQueryWrapper()
                .select(SysDict::getDictName, SysDict::getDictValue, SysDict::getDictLabel, SysDict::getShow,
                        SysDict::getDictSort).eq(SysDict::getShow, CommonConst.YES)
                .orderBy(true, true, SysDict::getDictSort);
        return super.select(wrapper);
    }

    public SysDict getSysDict(String dictName, String value) {
        LambdaQueryWrapper<SysDict> wrapper = super.getQueryWrapper().eq(SysDict::getDictName, dictName)
                .eq(SysDict::getDictValue, value);
        return super.selectOne(wrapper);
    }

    public List<SysDict> getSysDictByName(String dictName) {
        LambdaQueryWrapper<SysDict> wrapper = super.getQueryWrapper();
        wrapper.eq(SysDict::getDictName, dictName).orderBy(true, true, SysDict::getDictSort);
        return super.select(wrapper);
    }

    public List<SysDict> getSysDictByTitle(String dictName) {
        QueryWrapper<SysDict> wrapper = new QueryWrapper<SysDict>().select("DISTINCT " + SysDict.COL_DICT_NAME,
                SysDict.COL_TITLE).like(StringUtils.isNotEmpty(dictName), SysDict.COL_TITLE, dictName);
        return super.select(wrapper);
    }

    public SysDict getDefaultDict(String dictName) {
        LambdaQueryWrapper<SysDict> wrapper = super.getQueryWrapper().eq(SysDict::getDictName, dictName)
                .eq(SysDict::getIsDefault, CommonConst.YES);
        return super.selectOne(wrapper);
    }

    public Boolean existed(String dictName, String dictValue) {
        LambdaQueryWrapper<SysDict> wrapper = super.getQueryWrapper().eq(SysDict::getDictName, dictName)
                .eq(SysDict::getDictValue, dictValue);
        return super.existed(wrapper);
    }

    public Boolean deleteByDictName(String dictName) {
        LambdaUpdateWrapper<SysDict> wrapper = super.getUpdateWrapper().eq(SysDict::getDictName, dictName);
        return super.delete(wrapper);
    }

    public void deleteByDictList(List<String> dictList) {
        LambdaQueryWrapper<SysDict> wrapper = super.getQueryWrapper()
                .in(FuncUtil.isNotEmpty(dictList), SysDict::getDictName, dictList);
        super.delete(wrapper);
    }
}







