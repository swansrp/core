package com.bidr.authorization.service.cache;

import com.bidr.authorization.dao.entity.AcDept;
import com.bidr.authorization.dao.repository.AcDeptService;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Title: DeptCacheService
 * Description: ac_dept 内存缓存（T1.1 棒4 内核侧重建——原 bidr 侧 com.bidr.ai.inkhub.service.mdm
 * DeptCacheService 随桥收编退役，DynamicMemoryCache 语义迁内核：ac_dept 全量按 dept_id 建
 * DynamicMemoryCache，24 小时过期惰性刷新）。与平台侧 SysConfigCacheService 同款形态，
 * 经 MemoryCacheConfig 的 DynamicMemoryCacheInf 列表注入自动纳管，两交付形态（bidr/plsintec）
 * 均可用（缓存对象是内核表，客户无关）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Service
@RequiredArgsConstructor
public class DeptCacheService extends DynamicMemoryCache<AcDept> {

    /** 缓存过期分钟数（原 MDM_DEPT_CACHE_EXPIRED＝24h 口径平移） */
    private static final int DEPT_CACHE_EXPIRED_MINUTES = 24 * 60;

    private final AcDeptService acDeptService;

    @Override
    protected Map<String, AcDept> getCacheData() {
        List<AcDept> list = acDeptService.select();
        return ReflectionUtil.reflectToMap(list, AcDept::getDeptId);
    }

    @Override
    public int getExpired() {
        return DEPT_CACHE_EXPIRED_MINUTES;
    }
}
