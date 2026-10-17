package com.bidr.insight.chatbi.service;

import com.bidr.insight.chatbi.constant.param.ChatBiParam;
import com.bidr.llm.agent.conversation.AgentConversationRetentionProvider;
import com.bidr.platform.service.cache.SysConfigCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Title: ChatBiConversationRetentionProvider
 * Description: 通用对话存储保留天数注入（llm 上提后的 {@link AgentConversationRetentionProvider} SPI 实现）——
 * 沿用问数既有系统参数 {@link ChatBiParam#CONVERSATION_RETENTION_DAYS}（管理页可改，改后即生效），
 * 非法配置回落默认 10 天
 *
 * @author Sharp
 * @since 2026/8/22
 */
@Slf4j
@Component
public class ChatBiConversationRetentionProvider implements AgentConversationRetentionProvider {

    /**
     * 参数非法时的回落值（与通用存储缺省一致）
     */
    private static final int DEFAULT_RETENTION_DAYS = 10;

    private final SysConfigCacheService sysConfigCacheService;

    public ChatBiConversationRetentionProvider(SysConfigCacheService sysConfigCacheService) {
        this.sysConfigCacheService = sysConfigCacheService;
    }

    @Override
    public int retentionDays() {
        try {
            int days = sysConfigCacheService.getParamInt(ChatBiParam.CONVERSATION_RETENTION_DAYS);
            return days > 0 ? days : DEFAULT_RETENTION_DAYS;
        } catch (Exception e) {
            log.warn("读取问数对话保存天数参数失败，回落默认 {} 天", DEFAULT_RETENTION_DAYS);
            return DEFAULT_RETENTION_DAYS;
        }
    }
}
