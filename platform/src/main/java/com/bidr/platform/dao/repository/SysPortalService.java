package com.bidr.platform.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import com.bidr.kernel.vo.common.KeyValueResVO;
import com.bidr.platform.dao.entity.SysPortal;
import com.bidr.platform.dao.mapper.SysPortalMapper;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public List<KeyValueResVO> getPortalList(String name) {
        MPJLambdaWrapper<SysPortal> wrapper = new MPJLambdaWrapper<>();
        wrapper.selectAs(SysPortal::getDisplayName, KeyValueResVO::getLabel);
        wrapper.selectAs(SysPortal::getName, KeyValueResVO::getValue);
        wrapper.like(FuncUtil.isNotEmpty(name), SysPortal::getName, name);
        wrapper.or().like(FuncUtil.isNotEmpty(name), SysPortal::getDisplayName, name);
        wrapper.orderByAsc(SysPortal::getDisplayName);
        return selectJoinList(KeyValueResVO.class, wrapper);
    }

    public Boolean existedByName(String name) {
        LambdaQueryWrapper<SysPortal> wrapper = super.getQueryWrapper().eq(SysPortal::getName, name);
        return super.existed(wrapper);
    }
}
