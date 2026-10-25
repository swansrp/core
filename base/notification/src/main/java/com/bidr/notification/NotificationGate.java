package com.bidr.notification;

import com.bidr.kernel.utils.FuncUtil;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 站内信开关面（sys_config 缓存读，改配置即时生效免重启）：
 * <ul>
 *   <li>{@code notification.async-task.enabled}——任务失败提醒桥总开关（缺省开）；</li>
 *   <li>{@code notification.mute-types}——msgType 黑名单（逗号分隔；点对点静音用）。</li>
 * </ul>
 * 缺配置＝开/不静音（保守：宁多发不多吞——失败提醒是用户托付给系统的"死透必达"）。
 *
 * @author sharp (M21：站内信机制 2026-10-25)
 */
@Component
public class NotificationGate {

    private final ObjectProvider<SysConfigCacheService> configCache;

    public NotificationGate(ObjectProvider<SysConfigCacheService> configCache) {
        this.configCache = configCache;
    }

    /** 任务失败提醒桥开关（缺省 true） */
    public boolean asyncTaskEnabled() {
        // 🔴 配置项不存在时 getSysConfigValue(String) 会抛 PA_DATA_NOT_EXIST——必须吞掉按缺省开，
        // 否则 gate 在桥的 try 之外抛异常＝整条提醒链静默中断（真机实测：DEAD 任务零消息）
        String value = readConfigQuietly("notification.async-task.enabled");
        return FuncUtil.isEmpty(value) || "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /** 读配置（缺项/缓存不可用＝返回 null，绝不抛） */
    private String readConfigQuietly(String key) {
        SysConfigCacheService cache = configCache.getIfAvailable();
        if (cache == null) {
            return null;
        }
        try {
            return cache.getSysConfigValue(key);
        } catch (Exception e) {
            return null;
        }
    }

    /** msgType 是否被静音（notification.mute-types，逗号分隔） */
    public boolean muted(String msgType) {
        if (FuncUtil.isEmpty(msgType)) {
            return false;
        }
        String raw = readConfigQuietly("notification.mute-types");
        if (FuncUtil.isEmpty(raw)) {
            return false;
        }
        for (String t : raw.split(",")) {
            if (msgType.equals(t.trim())) {
                return true;
            }
        }
        return false;
    }
}
