package com.bidr.agent.console.capability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * Title: AgentCapabilityPackageRegistry
 * Description: 能力包注册中心（启动期收集、只读汇总，<b>不建表不自持状态</b>）——
 * 把各模块声明的 {@link AgentCapabilityPackage} Bean 归一成一份清单，供清单端点、管理页与
 * 后续装配单读取。形态对齐 {@code com.bidr.llm.agent.registry.AgentRegistryService}。
 * <p>
 * 冲突裁决分两档（"代码级零容忍、数据级不阻塞"）：
 * <ul>
 *     <li>包名非法或重复＝代码写错（两个模块抢同一分类名），启动抛错——勾选将来要落库，
 *     分类名歧义等于把工具挂给错误的 agent，不能留到运行期发现；</li>
 *     <li>同一工具名出现在两个包＝只 warn 不抛：语义上合法（一只工具可归多个分类），
 *     但交集计算时会被并集覆盖，必须在日志里看得见。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026-10-12
 */
@Slf4j
@Service
public class AgentCapabilityPackageRegistry {

    private final Map<String, CapabilityPackageDescriptor> indexed = new LinkedHashMap<>();

    private final Set<String> allToolNames;

    public AgentCapabilityPackageRegistry(List<AgentCapabilityPackage> packages) {
        Set<String> seenTools = new HashSet<>();
        List<String> sharedTools = new ArrayList<>();
        for (AgentCapabilityPackage pkg : packages) {
            CapabilityPackageDescriptor descriptor = toDescriptor(pkg);
            if (indexed.put(descriptor.getName(), descriptor) != null) {
                throw new IllegalStateException("能力包分类名重复注册: " + descriptor.getName()
                        + "（模块=" + descriptor.getModule() + "）");
            }
            for (String tool : descriptor.getTools()) {
                if (!seenTools.add(tool)) {
                    sharedTools.add(tool + "@" + descriptor.getName());
                }
            }
        }
        this.allToolNames = Collections.unmodifiableSet(new TreeSet<>(seenTools));
        if (!sharedTools.isEmpty()) {
            log.warn("以下工具被多个能力包声明（交集计算时按并集处理）: {}", sharedTools);
        }
        log.info("能力包注册中心就绪：包 {} 个、工具 {} 只", indexed.size(), allToolNames.size());
    }

    private CapabilityPackageDescriptor toDescriptor(AgentCapabilityPackage pkg) {
        String name = pkg.name();
        String declaredBy = pkg.getClass().getName();
        if (!StringUtils.hasText(name) || !name.equals(name.trim())) {
            throw new IllegalStateException("能力包分类名非法（须非空且无首尾空白）: [" + name + "] 登记件="
                    + declaredBy);
        }
        Set<CapabilityTag> tags = pkg.tags() == null ? Collections.emptySet() : pkg.tags();
        if (tags.isEmpty()) {
            throw new IllegalStateException("能力包 " + name + " 未声明能力标签（登记件=" + declaredBy
                    + "）——副作用档未知即不可勾选");
        }
        List<String> tools = pkg.tools();
        if (tools == null || tools.isEmpty()) {
            throw new IllegalStateException("能力包 " + name + " 未声明任何工具（登记件=" + declaredBy + "）");
        }
        CapabilityPackageDescriptor descriptor = new CapabilityPackageDescriptor();
        descriptor.setName(name);
        descriptor.setDisplayName(StringUtils.hasText(pkg.displayName()) ? pkg.displayName() : name);
        descriptor.setModule(pkg.module());
        descriptor.setDescription(pkg.description());
        descriptor.setTools(new ArrayList<>(tools));
        Set<String> tagNames = new TreeSet<>();
        for (CapabilityTag tag : tags) {
            tagNames.add(tag.name());
        }
        descriptor.setTags(new ArrayList<>(tagNames));
        return descriptor;
    }

    /** 全量清单（保持登记顺序） */
    public List<CapabilityPackageDescriptor> all() {
        return new ArrayList<>(indexed.values());
    }

    /** 按分类名查单条（不存在返回 null） */
    public CapabilityPackageDescriptor get(String name) {
        return name == null ? null : indexed.get(name);
    }

    /** 扁平工具名全集（升序；装配单与交集计算的比对基准） */
    public Set<String> toolNames() {
        return allToolNames;
    }
}
