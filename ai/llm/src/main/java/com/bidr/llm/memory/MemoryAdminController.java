package com.bidr.llm.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Title: MemoryAdminController
 * Description: 跨会话记忆管理端点（T4.3 记忆库）：按租户+用户查询记忆列表（kind/keyword 过滤）、
 * 删除单条、TTL 调整。鉴权沿用平台 /web/api 拦截链，不加 @IgnoreAuth；
 * 开关挂 llm.memory-api.enabled（默认开，同 agent 会话端点口径）。
 * <p>写口按派单要求提供 DELETE / POST（管理页专用，非业务主链路）。</p>
 *
 * @author Sharp
 * @since 2026/11/1
 */
@Slf4j
@RestController
@RequestMapping("/web/api/memory/admin")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "llm.memory-api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MemoryAdminController {

    private final MemoryService memoryService;

    /**
     * 记忆列表（租户+用户维度）：kind 可选过滤（FACT/PREFERENCE/PROFILE/SUMMARY），
     * keyword 可选正文包含过滤；按 priority 降序 → createdAt 降序返回（上限 {@link MemoryService#LIST_TOP_N}）
     */
    @GetMapping("/list/{tenantId}/{userId}")
    public List<MemoryRecord> list(@PathVariable("tenantId") Long tenantId,
                                   @PathVariable("userId") Long userId,
                                   @RequestParam(name = "kind", required = false) String kind,
                                   @RequestParam(name = "keyword", required = false) String keyword) {
        return memoryService.list(tenantId, userId, parseKind(kind), keyword);
    }

    /**
     * 删除单条记忆（recordId 路径参数 + tenantId/userId 定位维度）
     */
    @DeleteMapping("/{recordId}")
    public void delete(@PathVariable("recordId") String recordId,
                       @RequestParam("tenantId") Long tenantId,
                       @RequestParam("userId") Long userId) {
        memoryService.remove(tenantId, userId, recordId);
    }

    /**
     * TTL 调整：days&gt;0=按天顺延，days&lt;=0=改为永不过期
     */
    @PostMapping("/ttl")
    public void ttl(@RequestParam("recordId") String recordId,
                    @RequestParam("tenantId") Long tenantId,
                    @RequestParam("userId") Long userId,
                    @RequestParam("days") long days) {
        memoryService.adjustTtl(tenantId, userId, recordId, days);
    }

    /**
     * kind 参数解析（空=全部类别；非法值拒绝，防拼错静默查全量）
     */
    private MemoryKind parseKind(String kind) {
        if (!org.springframework.util.StringUtils.hasText(kind)) {
            return null;
        }
        return MemoryKind.valueOf(kind.trim().toUpperCase());
    }
}
