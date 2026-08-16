package com.bidr.insight.flow;

import com.bidr.insight.constant.param.ChatBiParam;
import com.bidr.llm.flow.FlowTraceRetentionProvider;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Title: ChatBiTraceRetentionProvider
 * Description: 轨迹保留天数接入——llm flow 引擎的轨迹记录器经 {@link FlowTraceRetentionProvider}
 * 实时读系统参数（管理页可改，改后即生效），非法配置回落默认 10 天。
 *
 * @author Sharp
 * @since 2026/8/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatBiTraceRetentionProvider implements FlowTraceRetentionProvider {

    /**
     * 参数非法/读取失败时的回落保留天数
     */
    private static final int DEFAULT_RETENTION_DAYS = 10;

    private final SysConfigCacheService sysConfigCacheService;

    @Override
    public int retentionDays() {
        try {
            int days = sysConfigCacheService.getParamInt(ChatBiParam.TRACE_RETENTION_DAYS);
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取问数轨迹保存天数参数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
