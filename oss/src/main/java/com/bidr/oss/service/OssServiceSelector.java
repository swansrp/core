package com.bidr.oss.service;

import com.bidr.kernel.utils.DictEnumUtil;
import com.bidr.oss.constant.dict.OssServiceTypeDict;
import com.bidr.oss.constant.param.OssParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Title: OssServiceSelector
 * Description: 按运行参数选当前对象存储实现——sys_config {@code OSS_SERVER_TYPE} → {@link BaseOssService}。
 * 把"读配置→查字典→取实现"的三步收成一处，业务侧按 key 直写/直读字节时一律经它取实现，
 * 不得再直连某一家 SDK 的模板件（否则换存储底座只改参数救不回直连的腿）。
 * Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026/10/15
 */
@Component
public class OssServiceSelector {

    @Resource
    private SysConfigCacheService sysConfigCacheService;

    /**
     * 当前选型对应的实现。值缺失按 {@code getParamValueAvail} 语义抛错（装机必配项），
     * 值非法按 {@code DictEnumUtil.getEnumByValue} 语义抛错——配置错误要当场炸，不静默回落某一家。
     */
    public BaseOssService select() {
        String type = sysConfigCacheService.getParamValueAvail(OssParam.OSS_SERVER_TYPE);
        return DictEnumUtil.getEnumByValue(type, OssServiceTypeDict.class).getService();
    }
}
