package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.platform.dao.entity.SysDict;
import com.bidr.platform.dao.mapper.SysDictDao;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysDictService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/03/09 11:40
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
}
