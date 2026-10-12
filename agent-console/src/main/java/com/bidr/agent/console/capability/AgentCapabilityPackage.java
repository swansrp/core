package com.bidr.agent.console.capability;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Title: AgentCapabilityPackage
 * Description: 能力包登记 SPI——"一个系统级 agent 能干什么"的**代码侧正源**。
 * <p>
 * 一个能力包＝一组同副作用档的工具（分类名对应一个构建器），登记的单位是**包**不是单只工具：
 * 页面上勾选的是包名（将来落进 agent 定义行的 JSON 列），工具名只用于诊断与交集计算。
 * <p>
 * 三条约束（与运行时的关系，M14 红线）：
 * <ul>
 *     <li><b>登记≠挂载</b>：本 SPI 只出清单，不改变任何 agent 的实际工具面——真正把工具挂给模型
 *     仍由消费方代码（如 InkHub 的 feature 工具面）决定；能力包缺席时工具**降级不挂**，绝不让应用起不来；</li>
 *     <li><b>工具代码零改动</b>：登记件是旁路声明，被登记的工具类不实现本接口、不加注解；</li>
 *     <li><b>工具名必须写注册名</b>：LangChain4j 在 {@code @Tool} 未写显式 name 时按 **camelCase 方法名**
 *     注册，手写 snake_case 会静默失配（有守卫测试逐类反射比对，见 LlmCapabilityPackageConfigTest）。</li>
 * </ul>
 *
 * @author Sharp
 * @since 2026-10-12
 */
public interface AgentCapabilityPackage {

    /** 分类名（全局唯一，小写短横线；agent 定义里存的就是这个值） */
    String name();

    /** 显示名（管理页与诊断清单） */
    String displayName();

    /** 来源模块标识（如 llm / inkhub-writer；只用于清单分组展示） */
    String module();

    /** 包内工具的注册名（非空、无重复） */
    List<String> tools();

    /** 副作用档（见 {@link CapabilityTag}；勾选闸门与只读判据按它筛） */
    Set<CapabilityTag> tags();

    /** 说明（可空，管理页悬浮文案） */
    default String description() {
        return null;
    }

    /**
     * 声明式构造（供登记件一行给出一个包）：工具名与标签都做**深拷贝**为不可变视图，
     * 保持顺序＝声明顺序（清单展示与差异比对要稳定）
     */
    static AgentCapabilityPackage of(String name, String displayName, String module,
                                     String description, Set<CapabilityTag> tags, String... tools) {
        final List<String> toolNames = Collections.unmodifiableList(
                tools == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(tools)));
        final Set<CapabilityTag> capabilityTags = tags == null ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
        return new AgentCapabilityPackage() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String displayName() {
                return displayName;
            }

            @Override
            public String module() {
                return module;
            }

            @Override
            public List<String> tools() {
                return toolNames;
            }

            @Override
            public Set<CapabilityTag> tags() {
                return capabilityTags;
            }

            @Override
            public String description() {
                return description;
            }
        };
    }
}
