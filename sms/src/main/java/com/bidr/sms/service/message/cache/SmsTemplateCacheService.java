package com.bidr.sms.service.message.cache;

import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.utils.ReflectionUtil;
import com.bidr.sms.dao.entity.SaSmsTemplate;
import com.bidr.sms.dao.repository.SaSmsTemplateService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * Title: SmsTemplateCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/03/09 15:10
 */
@Service
public class SmsTemplateCacheService extends DynamicMemoryCache<SaSmsTemplate> {

    @Resource
    private SaSmsTemplateService saSmsTemplateService;

    @Override
    protected Map<String, SaSmsTemplate> getCacheData() {
        List<SaSmsTemplate> list = saSmsTemplateService.getSmsTemplateCache();
        return ReflectionUtil.reflectToMap(list, SaSmsTemplate::getSmsType);
    }
}
