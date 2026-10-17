package com.bidr.agent.console.capability.builtin;

import com.bidr.agent.console.capability.AgentCapabilityPackage;
import com.bidr.agent.console.capability.AgentCapabilityPackageRegistry;
import com.bidr.agent.console.capability.CapabilityPackageDescriptor;
import com.bidr.agent.console.capability.CapabilityTag;
import com.bidr.llm.agent.AgentAskUserTool;
import com.bidr.llm.agent.AgentPlanTools;
import com.bidr.llm.agent.AgentToolRecall;
import com.bidr.llm.agent.FileMarkdownTool;
import com.bidr.llm.agent.tools.ExternalRuntimeTools;
import com.bidr.llm.agent.tools.RunFlowTools;
import dev.langchain4j.agent.tool.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: LlmCapabilityPackageConfigTest
 * Description: 登记件与真工具的<b>名字对账</b>——声明的工具名必须逐只等于 LangChain4j 实际注册的
 * 名字（{@code @Tool} 无显式 name 时＝camelCase 方法名）。手写清单一旦漂移，勾选面与工具面会静默
 * 失配（框架既有踩点：烧掉数百轮才发现），所以这里用反射取真名做双向断言，而不是照抄字面量再自证。
 *
 * @author Sharp
 * @since 2026-10-12
 */
class LlmCapabilityPackageConfigTest {

    /** 被登记的 llm 工具类（12 只的宿主） */
    private static final List<Class<?>> TOOL_CLASSES = Arrays.asList(
            AgentPlanTools.class, AgentAskUserTool.class, AgentToolRecall.class,
            ExternalRuntimeTools.class, RunFlowTools.class, FileMarkdownTool.class);

    private final LlmCapabilityPackageConfig config = new LlmCapabilityPackageConfig();

    private List<AgentCapabilityPackage> declared() {
        return Arrays.asList(config.planCapabilityPackage(), config.askUserCapabilityPackage(),
                config.toolRecallCapabilityPackage(), config.externalTaskCapabilityPackage(),
                config.runFlowCapabilityPackage(), config.fileReadCapabilityPackage());
    }

    /** 反射取实际注册名：@Tool(name=...) 非空取 name，否则取 camelCase 方法名 */
    private static Set<String> registeredNames(Class<?> toolClass) {
        Set<String> names = new LinkedHashSet<>();
        for (Method method : toolClass.getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            names.add(tool.name().isEmpty() ? method.getName() : tool.name());
        }
        return names;
    }

    @Test
    @DisplayName("登记的 12 只工具名与反射取到的实际注册名双向相等（不多不少不拼错）")
    void declaredToolNamesMatchRuntimeRegistration() {
        Set<String> declaredNames = new TreeSet<>();
        Map<String, String> ownerByTool = new LinkedHashMap<>();
        for (AgentCapabilityPackage pkg : declared()) {
            declaredNames.addAll(pkg.tools());
            for (String tool : pkg.tools()) {
                ownerByTool.put(tool, pkg.name());
            }
        }

        Set<String> reflectedNames = new TreeSet<>();
        for (Class<?> toolClass : TOOL_CLASSES) {
            Set<String> names = registeredNames(toolClass);
            assertTrue(!names.isEmpty(), toolClass.getSimpleName() + " 上找不到 @Tool 方法");
            reflectedNames.addAll(names);
        }

        assertEquals(reflectedNames, declaredNames,
                "登记清单与 @Tool 实际注册名不一致（左=真名，右=登记值）");
        assertEquals(12, declaredNames.size(), "llm 侧纯通用工具应为 12 只");
        assertEquals(12, ownerByTool.size());
    }

    @Test
    @DisplayName("六个能力包齐备且副作用档明确（外部效应不能标成只读）")
    void packagesAndTags() {
        AgentCapabilityPackageRegistry registry = new AgentCapabilityPackageRegistry(declared());
        List<CapabilityPackageDescriptor> all = registry.all();
        assertEquals(6, all.size());

        Map<String, Set<String>> tagsByName = new LinkedHashMap<>();
        for (CapabilityPackageDescriptor descriptor : all) {
            tagsByName.put(descriptor.getName(), new TreeSet<>(descriptor.getTags()));
            assertEquals(LlmCapabilityPackageConfig.MODULE, descriptor.getModule());
            assertNotNull(descriptor.getDisplayName());
        }

        assertEquals(new TreeSet<>(Arrays.asList("plan", "ask-user", "tool-recall",
                "external-task", "run-flow", "file-read")), new TreeSet<>(tagsByName.keySet()));
        assertEquals(new TreeSet<>(Arrays.asList(CapabilityTag.SESSION.name())), tagsByName.get("plan"));
        assertEquals(new TreeSet<>(Arrays.asList(CapabilityTag.SESSION.name())), tagsByName.get("ask-user"));
        assertEquals(new TreeSet<>(Arrays.asList(CapabilityTag.READ.name())), tagsByName.get("tool-recall"));
        assertEquals(new TreeSet<>(Arrays.asList(CapabilityTag.READ.name())), tagsByName.get("file-read"));
        assertEquals(new TreeSet<>(Arrays.asList(CapabilityTag.EXTERNAL_EFFECT.name())),
                tagsByName.get("external-task"));
        assertEquals(new TreeSet<>(Arrays.asList(CapabilityTag.EXTERNAL_EFFECT.name())),
                tagsByName.get("run-flow"));
    }

    @Test
    @DisplayName("外部任务包的五只工具全部引用框架常量（不手拼字符串）")
    void externalTaskToolsUseFrameworkConstants() {
        AgentCapabilityPackage pkg = config.externalTaskCapabilityPackage();
        List<String> expected = new ArrayList<>(Arrays.asList(
                ExternalRuntimeTools.TOOL_SUBMIT, ExternalRuntimeTools.TOOL_WAIT,
                ExternalRuntimeTools.TOOL_QUERY, ExternalRuntimeTools.TOOL_READ,
                ExternalRuntimeTools.TOOL_CANCEL));
        assertEquals(expected, pkg.tools());
    }
}
