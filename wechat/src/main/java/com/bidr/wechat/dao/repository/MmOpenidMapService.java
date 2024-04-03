package com.bidr.wechat.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.wechat.dao.entity.MmOpenidMap;
import com.bidr.wechat.dao.mapper.MmOpenidMapDao;
import org.springframework.stereotype.Service;

/**
 * Title: MmOpenidMapService
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/6/17 11:00
 */
@Service
public class MmOpenidMapService extends BaseSqlRepo<MmOpenidMapDao, MmOpenidMap> {


    public MmOpenidMap getOpenidMapByUnionId(String unionId) {
        LambdaQueryWrapper<MmOpenidMap> wrapper = super.getQueryWrapper();
        wrapper.eq(MmOpenidMap::getUnionId, unionId);
        return super.selectOne(wrapper);
    }

    public MmOpenidMap getOpenidMapByOpenId(String openId) {
        LambdaQueryWrapper<MmOpenidMap> wrapper = super.getQueryWrapper();
        wrapper.eq(MmOpenidMap::getOpenId, openId);
        return super.selectOne(wrapper);
    }
}





