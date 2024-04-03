package com.bidr.wechat.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.wechat.dao.entity.MmRoleTagMap;
import com.bidr.wechat.dao.mapper.MmRoleTagMapDao;
import org.springframework.stereotype.Service;

/**
 * Title: MmRoleTagMapService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/17 11:00
 */
@Service
public class MmRoleTagMapService extends BaseSqlRepo<MmRoleTagMapDao, MmRoleTagMap> {

    public MmRoleTagMap getOneRoleTagMapByRoleId(Long roleId) {
        LambdaQueryWrapper<MmRoleTagMap> wrapper = super.getQueryWrapper();
        wrapper.eq(MmRoleTagMap::getRoleId, roleId);
        return super.selectOne(wrapper);
    }
}


