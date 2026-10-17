package com.bidr.agent.console;

import com.bidr.agent.console.capability.AgentCapabilityPackageRegistry;
import com.bidr.agent.console.dto.CapabilityManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Title: AgentCapabilityController
 * Description: 能力包清单端点（<b>只读，M14 棒 1 无写口</b>）——列出代码登记上来的能力包、
 * 包内工具名与副作用档，供"系统管理 → 能力包清单"页渲染与诊断。
 * <p>
 * 鉴权与装配口径同 {@code com.bidr.llm.agent.registry.AgentRegistryController}（/web/api 拦截链）；
 * 勾选、agent 定义与写权限在棒 6 才出现，本端点不提供任何增删改。
 *
 * @author Sharp
 * @since 2026-10-12
 */
@RestController
@RequestMapping("/web/api/agent/capability")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "agent-console.api", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentCapabilityController {

    private final AgentCapabilityPackageRegistry registry;

    /** 全量能力包清单（含扁平工具名全集，前端不必自行并集） */
    @GetMapping("/packages")
    public CapabilityManifest packages() {
        CapabilityManifest manifest = new CapabilityManifest();
        manifest.setPackages(registry.all());
        manifest.setToolNames(registry.toolNames());
        return manifest;
    }
}
