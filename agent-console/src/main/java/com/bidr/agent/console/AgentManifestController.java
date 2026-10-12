package com.bidr.agent.console;

import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.repository.AgentManifestService;
import com.bidr.agent.console.dto.AgentManifestView;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Title: AgentManifestController
 * Description: 会话装配单端点（<b>只读，M14 棒 2 无写口</b>）——按会话查"当时手里有什么"：
 * 工具名清单、模型档、注入的技能与版本、提示词段。落库腿在 {@code AgentManifestRecorder}。
 * <p>
 * 装配与鉴权口径同 {@link AgentCapabilityController}（/web/api 拦截链，框架平台表不参与空间隔离，
 * 诊断面跨空间可读）。本端点不提供任何增删改；写快照的开关是 {@code agent-console.manifest.enabled}，
 * 关掉后旧行仍可读。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@RestController
@RequestMapping("/web/api/agent/manifest")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentManifestController {

    /** 列表腿默认与硬上限（诊断页一次别拉太多，装配单行含三段 JSON） */
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AgentManifestService repo;

    /** 按会话查装配单（没有这场的记录返回空响应体：棒 2 之前发起的历史会话本就没有快照） */
    @GetMapping("/{sessionId}")
    public AgentManifestView bySession(@PathVariable("sessionId") String sessionId) {
        return AgentManifestView.of(repo.findBySession(sessionId));
    }

    /** 最近的装配单（agentKey 空＝不限 agent，按定形时刻新→旧） */
    @GetMapping("/recent")
    public List<AgentManifestView> recent(@RequestParam(value = "agentKey", required = false) String agentKey,
                                          @RequestParam(value = "limit", required = false) Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        List<AgentManifestView> views = new ArrayList<>();
        for (AgentManifest row : repo.listRecent(agentKey, size)) {
            views.add(AgentManifestView.of(row));
        }
        return views;
    }
}
