package com.bidr.framework.service.cache;

import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * Title: FrameCacheService
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/30 13:22
 */
@Service
public class FrameCacheService {
    @Resource
    private ParamCacheService paramCacheService;
    @Resource
    private DictCacheService dictCacheService;
    @Resource
    private DictTreeCacheService dictTreeCacheService;
}
