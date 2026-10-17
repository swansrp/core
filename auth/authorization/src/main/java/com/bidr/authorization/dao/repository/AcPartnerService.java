package com.bidr.authorization.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.mapper.AcPartnerDao;
import com.bidr.authorization.vo.partner.QueryPartnerReq;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

/**
 * Title: AcPartnerService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 16:40
 */
@Service
public class AcPartnerService extends BaseSqlRepo<AcPartnerDao, AcPartner> {

    public AcPartner getByAppKey(String appKey) {
        LambdaQueryWrapper<AcPartner> wrapper = super.getQueryWrapper();
        wrapper.eq(AcPartner::getAppKey, appKey);
        return super.selectOne(wrapper);
    }

    public Page<AcPartner> queryPartner(QueryPartnerReq req) {
        LambdaQueryWrapper<AcPartner> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(req.getPlatform()), AcPartner::getPlatform, req.getPlatform());
        return super.select(wrapper, req);
    }
}
