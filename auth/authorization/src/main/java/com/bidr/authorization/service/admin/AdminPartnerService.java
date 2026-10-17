package com.bidr.authorization.service.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.authorization.dao.entity.AcPartner;
import com.bidr.authorization.dao.repository.AcPartnerService;
import com.bidr.authorization.vo.partner.PartnerReq;
import com.bidr.authorization.vo.partner.PartnerRes;
import com.bidr.authorization.vo.partner.QueryPartnerReq;
import com.bidr.authorization.vo.partner.QueryPartnerRes;
import com.bidr.kernel.config.response.Resp;
import com.bidr.kernel.constant.dict.common.ActiveStatusDict;
import com.bidr.kernel.utils.RandomUtil;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Title: AdminPartnerService
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2023/05/30 17:34
 */
@Service
@RequiredArgsConstructor
public class AdminPartnerService {

    private final AcPartnerService acPartnerService;


    public Page<QueryPartnerRes> query(QueryPartnerReq req) {
        Page<AcPartner> partner = acPartnerService.queryPartner(req);
        return Resp.convert(partner, QueryPartnerRes.class);
    }

    @Transactional(rollbackFor = Exception.class)
    public PartnerRes add(PartnerReq req) {
        AcPartner partner = ReflectionUtil.copy(req, AcPartner.class);
        String appKey = RandomUtil.getString(8);
        String appSecret = RandomUtil.getString(16);
        partner.setAppKey(appKey);
        partner.setAppSecret(appSecret);
        partner.setStatus(ActiveStatusDict.ACTIVATE.getValue());
        acPartnerService.insert(partner);
        return new PartnerRes(appKey, appSecret);

    }
}
