package com.bidr.sms.dao.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.dao.mapper.SaSmsTemplateDao;
import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Title: SaSmsTemplateService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 10:08
 */
@Service
public class SaSmsTemplateService extends BaseSqlRepo<SaSmsTemplateDao, SaSmsTemplate> {

    public SaSmsTemplate selectOneBySmsType(Object smsType) {
        LambdaQueryWrapper<SaSmsTemplate> wrapper = getQueryWrapper().eq(SaSmsTemplate::getSmsType, smsType);
        return super.selectOne(wrapper);
    }

    public List<SaSmsTemplate> getSmsTemplateCache() {
        return super.list();
    }

    public SaSmsTemplate selectOneByTemplateCode(String templateCode) {
        LambdaQueryWrapper<SaSmsTemplate> wrapper = getQueryWrapper().eq(SaSmsTemplate::getTemplateCode, templateCode);
        return super.selectOne(wrapper);
    }

    public List<SaSmsTemplate> getNoConfirmTemplate() {
        LambdaQueryWrapper<SaSmsTemplate> wrapper = getQueryWrapper().ne(SaSmsTemplate::getConfirmStatus,
                CommonConst.YES);
        return super.list(wrapper);
    }

    public List<SaSmsTemplate> getTemplateByPlatform(String platform) {
        LambdaQueryWrapper<SaSmsTemplate> wrapper = getQueryWrapper().eq(SaSmsTemplate::getPlatform, platform);
        return super.list(wrapper);
    }

    public boolean existedSmsType(String smsType) {
        LambdaQueryWrapper<SaSmsTemplate> wrapper = getQueryWrapper().eq(SaSmsTemplate::getSmsType, smsType);
        return super.existed(wrapper);
    }

    public List<SaSmsTemplate> getAllSign() {
        LambdaQueryWrapper<SaSmsTemplate> wrapper = getQueryWrapper().select(SaSmsTemplate::getSign)
                .groupBy(SaSmsTemplate::getSign);
        return super.select(wrapper);
    }
}


















