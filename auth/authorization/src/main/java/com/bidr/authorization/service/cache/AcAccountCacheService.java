package com.bidr.authorization.service.cache;

import com.bidr.authorization.dao.entity.AcAccount;
import com.bidr.authorization.dao.repository.AcAccountService;
import com.bidr.kernel.cache.DynamicMemoryCache;
import com.bidr.kernel.utils.ReflectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Title: AcAccountCacheService
 * Description: ac_account 内存缓存（T1.1 棒4 内核侧重建——原 bidr 侧 com.bidr.ai.inkhub.service.mdm
 * UserCacheService 随桥收编退役，DynamicMemoryCache 语义迁内核：ac_account 全量按账号 id 建
 * DynamicMemoryCache，24 小时过期惰性刷新。原类 getCacheData 上的 @Transactional（只读语义）
 * 属误挂，重建时不再携带）。与平台侧 SysConfigCacheService 同款形态，经 MemoryCacheConfig 的
 * DynamicMemoryCacheInf 列表注入自动纳管，两交付形态均可用（缓存对象是内核表，客户无关）。
 * <p>
 * 🔴 类名由 UserCacheService 改为按缓存实体命名（对齐 SysConfigCacheService 口径）：存量项目仓的
 * 脚手架副本 com.bidr.*.service.mdm.UserCacheService 与本类简名相同，两侧默认 bean 名都是
 * userCacheService，component-scan 覆盖 com.bidr 时启动即 ConflictingBeanDefinitionException。
 * 实测 workspace 内 8 个业务仓带该副本，故改名收口在内核侧（改名后 bean 名 acAccountCacheService）。
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-01
 */
@Service
@RequiredArgsConstructor
public class AcAccountCacheService extends DynamicMemoryCache<AcAccount> {

    /** 缓存过期分钟数（原 MDM_USER_CACHE_EXPIRED＝24h 口径平移） */
    private static final int USER_CACHE_EXPIRED_MINUTES = 24 * 60;

    private final AcAccountService accountService;

    @Override
    protected Map<String, AcAccount> getCacheData() {
        List<AcAccount> list = accountService.select();
        return ReflectionUtil.reflectToMap(list, AcAccount::getId);
    }

    @Override
    public int getExpired() {
        return USER_CACHE_EXPIRED_MINUTES;
    }
}
