package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.platform.dao.entity.SysDictType;
import com.bidr.platform.dao.mapper.SysDictTypeDao;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SysDictTypeService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 11:00
 */
@Service
public class SysDictTypeService extends BaseSqlRepo<SysDictTypeDao, SysDictType> {

    List<SysDictType> getAllEnableSysDict() {
        LambdaQueryWrapper<SysDictType> queryWrapper = super.getQueryWrapper();
        return null;
    }

}
