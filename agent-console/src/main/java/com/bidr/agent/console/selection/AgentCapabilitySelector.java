package com.bidr.agent.console.selection;

import com.bidr.agent.console.capability.AgentCapabilityPackageRegistry;
import com.bidr.agent.console.capability.CapabilityPackageDescriptor;
import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.dao.repository.AgentDefinitionService;
import com.bidr.llm.agent.AgentToolSurfaceFilter;
import dev.langchain4j.agent.tool.ToolSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: AgentCapabilitySelector
 * Description: 按平台勾选收窄工具面（M14 棒6，交集公式的实现件）。
 *
 * <p><b>公式落在这一处</b>（立项稿 §3.3）：
 * {@code 本会话工具集 = feature 面的代码工具面（上限，硬） ∩ 勾选的能力包并集（数据，只能更小）
 * ∪ 业务代码追加的工具（无条件生效）}。这里实现的是后两项，前一项由调用方递进来的
 * {@code resolved} 已经带着——它本身就出自 feature 面选出来的那个类，
 * 所以"扩权"在这条链上<b>没有入口</b>：勾一个 feature 面里没有的包，交集仍是空。</p>
 *
 * <p>🔴 <b>"∪ 业务追加"这一项的实现方式值得说清</b>：判据不是"这工具是不是业务加的"（框架不知道），
 * 而是<b>"这只工具名在不在能力包登记表里"</b>。§八 已裁定 12 只咬业务工具不做抽象、不登记，
 * 所以它们天然不在 {@code registry.toolNames()} 里 ⇒ 一律保留。这条判据是结构性的：
 * 业务侧加一只新工具不需要在平台侧登记什么，也关不掉它——"勾选只能减"减的是登记过的面。</p>
 *
 * <p><b>三态</b>：无行、{@code capability_packages} 为 NULL＝放行（＝改前形态，上线零漂移的落点）；
 * {@code []}＝登记过的工具全不给；非空＝按并集取交集。
 * 勾选里出现<b>未注册</b>的包名（包改名或被删）＝该名字不贡献任何工具＝<b>按窄处理</b>并 warn：
 * 反过来"脏名字就当没看见全放行"会让一次包改名静默变成一次扩权。</p>
 *
 * <p>读一次库＝每场会话一次（本方法只在工具面定形时被调，不进模型轮次循环），
 * 所以这里<b>刻意不做缓存</b>：缓存会给"改完勾选多久生效"加一个没人能答的窗口。</p>
 *
 * @author Sharp
 * @since 2026-10-13
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "agent-console.selection", name = "enabled", havingValue = "true")
public class AgentCapabilitySelector implements AgentToolSurfaceFilter {

    private final AgentDefinitionService definitions;

    private final AgentCapabilityPackageRegistry registry;

    public AgentCapabilitySelector(AgentDefinitionService definitions,
                                   AgentCapabilityPackageRegistry registry) {
        this.definitions = definitions;
        this.registry = registry;
    }

    @Override
    public List<ToolSpecification> restrict(String agentKey, List<ToolSpecification> resolved) {
        if (!StringUtils.hasText(agentKey) || resolved == null) {
            // 没有会话身份就没有可判的 agent——不过滤；这一支覆盖直连小请求与框架内自测路径
            return resolved;
        }
        AgentDefinition definition = definitions.findByAgentKey(agentKey);
        if (definition == null) {
            return resolved;
        }
        SelectionColumn selection = SelectionColumn.parse(definition.getCapabilityPackages(),
                "sys_agent_definition.capability_packages");
        if (selection.isUnset()) {
            return resolved;
        }
        Set<String> granted = new HashSet<>();
        List<String> unknownPackages = new ArrayList<>();
        for (String packageName : selection.getNames()) {
            CapabilityPackageDescriptor descriptor = registry.get(packageName);
            if (descriptor == null) {
                unknownPackages.add(packageName);
                continue;
            }
            granted.addAll(descriptor.getTools());
        }
        if (!unknownPackages.isEmpty()) {
            log.warn("勾选里有未注册的能力包（按窄处理，这些包的工具本次不给）: agent={} 包={} —— "
                    + "多半是包改名或下架后定义行没跟着刷", agentKey, unknownPackages);
        }
        Set<String> registered = registry.toolNames();
        List<ToolSpecification> kept = new ArrayList<>(resolved.size());
        for (ToolSpecification spec : resolved) {
            String name = spec == null ? null : spec.name();
            if (name == null || !registered.contains(name)) {
                // 未登记＝框架不认识＝业务追加或框架自带的面外件，一律保留（勾选无权关它）
                kept.add(spec);
                continue;
            }
            if (granted.contains(name)) {
                kept.add(spec);
            }
        }
        log.info("工具面收窄: agent={} 勾选={} → {}/{} 只（其中未登记保留 {} 只）", agentKey, selection,
                kept.size(), resolved.size(), countUnregistered(kept, registered));
        return kept;
    }

    private static int countUnregistered(List<ToolSpecification> kept, Set<String> registered) {
        int count = 0;
        for (ToolSpecification spec : kept) {
            String name = spec == null ? null : spec.name();
            if (name != null && !registered.contains(name)) {
                count++;
            }
        }
        return count;
    }
}
