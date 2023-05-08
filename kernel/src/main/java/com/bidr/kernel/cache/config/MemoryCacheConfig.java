package com.bidr.kernel.cache.config;

import com.bidr.kernel.cache.DynamicMemoryCacheInf;
import com.bidr.kernel.cache.MemoryCacheInf;
import com.diboot.core.cache.StaticMemoryCacheManager;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: MemoryCacheConfig
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @since 2023/02/17 09:47
 */
@Configuration
public class MemoryCacheConfig {
    @Autowired(required = false)
    private List<DynamicMemoryCacheInf<?>> dynamicMemoryCacheList;

    @Autowired(required = false)
    private List<MemoryCacheInf<?>> staticMemoryCacheList;

    @Bean
    public DynamicMemoryCacheManager dynamicMemoryCacheManager() {
        Map<String, Integer> dynamicCaches = new HashMap<>();
        if (CollectionUtils.isNotEmpty(dynamicMemoryCacheList)) {
            for (DynamicMemoryCacheInf<?> cache : dynamicMemoryCacheList) {
                dynamicCaches.put(cache.getCacheName(), cache.getExpired());
            }
        }
        return new DynamicMemoryCacheManager(dynamicCaches);
    }

    @Bean
    public StaticMemoryCacheManager staticMemoryCacheManager() {

        List<String> staticCaches = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(staticMemoryCacheList)) {
            for (MemoryCacheInf<?> cache : staticMemoryCacheList) {
                staticCaches.add(cache.getCacheName());
            }
        }
        return new StaticMemoryCacheManager(staticCaches.toArray(new String[0]));
    }

}
