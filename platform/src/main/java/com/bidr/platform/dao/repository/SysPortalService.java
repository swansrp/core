package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.platform.dao.entity.SysPortal;
import com.bidr.platform.dao.mapper.SysPortalMapper;
import org.springframework.stereotype.Service;

/**
 * Title: SysPortalService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/11/21 18:00
 */
@Service
public class SysPortalService extends BaseSqlRepo<SysPortalMapper, SysPortal> {

    public SysPortal getByName(String name) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper().eq(SysPortal::getName, name);
        return super.selectOne(wrapper);
    }
}
