package com.bidr.kernel.cache;

import org.springframework.boot.CommandLineRunner;

/**
 * Title: MemoryCache
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2022/12/14 09:59
 */
public interface MemoryCacheInf<T> extends CommandLineRunner {
    /**
     * cache名称
     *
     * @return cache名称
     */
    default String getCacheName() {
        return this.getClass().getName();
    }

    /**
     * 获取缓存
     *
     * @return 缓存
     */
    T getCache(Object key);

    /**
     * 更新缓存
     */
    void refresh();

    /**
     * 初始化缓存
     */
    void init();

    @Override
    default void run(String... args) throws Exception {
        init();
    }
}
