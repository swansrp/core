package com.bidr.sms.dao.repository;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bidr.sms.dao.entity.SaSmsSend;
import com.bidr.sms.dao.mapper.SaSmsSendDao;
import com.bidr.sms.vo.SmsHistoryReq;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.FuncUtil;
import org.springframework.stereotype.Service;

/**
 * Title: SaSmsSendService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 10:07
 */
@DS("SMS")
@Service
public class SaSmsSendService extends BaseSqlRepo<SaSmsSendDao, SaSmsSend> {

    public SaSmsSend getSaSmsSendByRequestId(String requestId) {
        LambdaQueryWrapper<SaSmsSend> wrapper = super.getQueryWrapper().eq(SaSmsSend::getSendId, requestId);
        return super.selectOne(wrapper);
    }

    public Page<SaSmsSend> getSaSmsSend(SmsHistoryReq req) {
        LambdaQueryWrapper<SaSmsSend> wrapper = super.getQueryWrapper();
        wrapper.eq(FuncUtil.isNotEmpty(req.getPlatform()), SaSmsSend::getPlatform, req.getPlatform())
                .eq(FuncUtil.isNotEmpty(req.getPhoneNumber()), SaSmsSend::getMobile, req.getPhoneNumber())
                .eq(FuncUtil.isNotEmpty(req.getTemplateCode()), SaSmsSend::getTemplateCode, req.getTemplateCode())
                .ge(FuncUtil.isNotEmpty(req.getQueryStartAt()), SaSmsSend::getSendAt, req.getQueryStartAt())
                .le(FuncUtil.isNotEmpty(req.getQueryEndAt()), SaSmsSend::getSendAt, req.getQueryEndAt())
                .orderBy(true, false, SaSmsSend::getResponseAt);
        return super.select(wrapper, req.getCurrentPage(), req.getPageSize());

    }
}









