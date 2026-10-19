package com.bidr.agent.console;

import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.repository.AgentManifestService;
import com.bidr.agent.console.dto.AgentManifestView;
import com.bidr.kernel.config.response.RespConvert;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.agent.conversation.AgentConversation;
import com.bidr.llm.agent.conversation.AgentConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Title: AgentManifestController
 * Description: 会话装配单端点（<b>只读，M14 棒 2 无写口</b>）——按会话查"当时手里有什么"：
 * 工具名清单、模型档、注入的技能与版本、提示词段，以及棒 2 增量加的两件重载荷：
 * 提示词全文（{@code include=prompt}）与对话正文（{@code include=conversation}）。
 * <p>
 * 装配与鉴权口径同 {@link AgentCapabilityController}（/web/api 拦截链，框架平台表不参与空间隔离，
 * 诊断面跨空间可读）。两件重载荷<b>默认不下发</b>，显式展开须过身份闸：只有该会话的发起人本人
 * 或平台管理员能拿到——装配单是跨空间可读的诊断面，把"原文"随手发给任何登录用户等于把
 * 别人的提问内容与指令文本公开了。本端点不提供任何增删改；写快照的开关是
 * {@code agent-console.manifest.enabled}，关掉后旧行仍可读。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Slf4j
@RestController
@RequestMapping("/web/api/agent/manifest")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentManifestController {

    /** 列表腿默认与硬上限（诊断页一次别拉太多，装配单行含三段 JSON） */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    /** include 取值（逗号分隔可同要两件） */
    private static final String INCLUDE_PROMPT = "prompt";
    private static final String INCLUDE_CONVERSATION = "conversation";

    private final AgentManifestService repo;

    /** 通用历史对话层（对话正文按 conversationId 现读；未接入 Redis 时为 null＝只报定位不报正文） */
    private final ObjectProvider<AgentConversationService> conversationProvider;

    /** 当前访问人（同 llm 侧对话端点的 SPI 口径，无实现回落 null＝只有管理员能展开） */
    private final ObjectProvider<OperatorResolver> operatorResolver;

    /**
     * 按会话查装配单（没有这场的记录返回空响应体：棒 2 之前发起的历史会话本就没有快照）。
     * {@code include} 为空＝只回清单类字段；带 prompt／conversation 时先过身份闸再取原文
     */
    @GetMapping("/{sessionId}")
    public AgentManifestView bySession(@PathVariable("sessionId") String sessionId,
                                       @RequestParam(value = "include", required = false) String include) {
        AgentManifest row = repo.findBySession(sessionId);
        AgentManifestView view = AgentManifestView.of(row);
        RespConvert.customBindConvert(view, AgentManifestView.class);
        if (view == null || !StringUtils.hasText(include)) {
            return view;
        }
        requireExpandable(row);
        String wanted = include.toLowerCase(Locale.ROOT);
        if (wanted.contains(INCLUDE_PROMPT)) {
            view.setPromptText(row.getPromptText());
        }
        if (wanted.contains(INCLUDE_CONVERSATION)) {
            view.setConversation(loadConversation(row.getConversationId()));
        }
        return view;
    }

    /** 最近的装配单（agentKey 空＝不限 agent，按定形时刻新→旧；列表一律不带重载荷） */
    @GetMapping("/recent")
    public List<AgentManifestView> recent(@RequestParam(value = "agentKey", required = false) String agentKey,
                                          @RequestParam(value = "limit", required = false) Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<AgentManifestView> views = new ArrayList<>();
        for (AgentManifest row : repo.listRecent(agentKey, size)) {
            views.add(AgentManifestView.of(row));
        }
        RespConvert.customBindConvert(views, AgentManifestView.class);
        return views;
    }

    /**
     * 展开权限：发起人本人（按装配单落的 asked_by 比服务端权威身份）或平台管理员。
     * 管理员判定走 authorization 的 permitService 反射取 bean（本模块对 authorization 的编译依赖只服务
     * {@code @BindUser} 的姓名翻译，权限判定不吃它），取不到＝按非管理员处理，
     * 宁可少给一个入口，不能让缺 bean 变成放开原文
     */
    private void requireExpandable(AgentManifest row) {
        String operator = currentOperator();
        if (StringUtils.hasText(operator) && operator.equals(row.getAskedBy())) {
            return;
        }
        if (isAdmin()) {
            return;
        }
        throw new NoticeException("提示词全文与对话原文只给发起人本人和平台管理员展开");
    }

    private String currentOperator() {
        OperatorResolver resolver = operatorResolver.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.currentOperator();
        } catch (Exception e) {
            log.warn("装配单展开取访问人失败（按无权处理）: error={}", e.getMessage());
            return null;
        }
    }

    /** 正文现读对话层：定位缺失或原文已过保留期都返回 null，页面按 conversationId 区分两种文案 */
    private AgentConversation loadConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return null;
        }
        AgentConversationService service = conversationProvider.getIfAvailable();
        return service == null ? null : service.getConversation(conversationId);
    }

    private boolean isAdmin() {
        try {
            return Boolean.TRUE.equals(BeanUtil.invoke("permitService", "isAdmin", Boolean.class));
        } catch (Exception e) {
            return false;
        }
    }
}
