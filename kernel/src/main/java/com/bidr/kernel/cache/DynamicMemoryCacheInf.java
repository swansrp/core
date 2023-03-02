package com.bidr.kernel.cache;

/**
 * Title: DynamicMemoryCacheInf
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/02/17 11:10
 */
public interface DynamicMemoryCacheInf<T> extends MemoryCacheInf<T> {
    /**
     * 配置过期时间
     *
     * @return
     */
    int getExpired();
}
