package com.bidr.agent.console.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentCapabilityPackageRegistryTest
 * Description: 注册中心的收集与校验口径——代码级冲突（分类名重复/非法、无标签、无工具）启动抛错；
 * 同一工具被两个包声明只降级为 warn（语义合法，但交集按并集，必须可观测）。
 *
 * @author Sharp
 * @since 2026-10-12
 */
class AgentCapabilityPackageRegistryTest {

    private static AgentCapabilityPackage pkg(String name, Set<CapabilityTag> tags, String... tools) {
        return AgentCapabilityPackage.of(name, name, "test", null, tags, tools);
    }

    @Test
    @DisplayName("正常收集：包按登记顺序、工具名并集升序、缺 displayName 回落分类名")
    void collectsInDeclarationOrder() {
        AgentCapabilityPackageRegistry registry = new AgentCapabilityPackageRegistry(Arrays.asList(
                pkg("b", EnumSet.of(CapabilityTag.WRITE), "zetaTool", "alphaTool"),
                pkg("a", EnumSet.of(CapabilityTag.READ), "midTool")));

        List<CapabilityPackageDescriptor> all = registry.all();
        assertEquals(Arrays.asList("b", "a"), Arrays.asList(all.get(0).getName(), all.get(1).getName()));
        assertEquals(all.get(0).getName(), all.get(0).getDisplayName());
        assertEquals(new TreeSet<>(Arrays.asList("alphaTool", "midTool", "zetaTool")),
                registry.toolNames());
        assertEquals("a", registry.get("a").getName());
        assertNull(registry.get("missing"));
        assertNull(registry.get(null));
    }

    @Test
    @DisplayName("分类名重复＝代码写错，启动抛错且指明重复项")
    void duplicatePackageNameFailsFast() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> new AgentCapabilityPackageRegistry(Arrays.asList(
                        pkg("plan", EnumSet.of(CapabilityTag.SESSION), "submitPlan"),
                        pkg("plan", EnumSet.of(CapabilityTag.READ), "otherTool"))));
        assertTrue(e.getMessage().contains("plan"), e.getMessage());
    }

    @Test
    @DisplayName("非法登记逐项拒绝：空名/带首尾空格/无标签/无工具")
    void rejectsInvalidDeclarations() {
        assertThrows(IllegalStateException.class, () -> new AgentCapabilityPackageRegistry(
                Collections.singletonList(pkg(" ", EnumSet.of(CapabilityTag.READ), "t"))));
        assertThrows(IllegalStateException.class, () -> new AgentCapabilityPackageRegistry(
                Collections.singletonList(pkg(" plan ", EnumSet.of(CapabilityTag.READ), "t"))));
        assertThrows(IllegalStateException.class, () -> new AgentCapabilityPackageRegistry(
                Collections.singletonList(pkg("plan", Collections.emptySet(), "t"))));
        assertThrows(IllegalStateException.class, () -> new AgentCapabilityPackageRegistry(
                Collections.singletonList(pkg("plan", null, (String[]) null))));
        assertThrows(IllegalStateException.class, () -> new AgentCapabilityPackageRegistry(
                Collections.singletonList(pkg("plan", EnumSet.of(CapabilityTag.READ)))));
    }

    @Test
    @DisplayName("零登记也不炸（能力包缺席＝清单为空，不影响应用启动）")
    void emptyRegistrationIsNotFatal() {
        AgentCapabilityPackageRegistry registry = new AgentCapabilityPackageRegistry(Collections.emptyList());
        assertTrue(registry.all().isEmpty());
        assertTrue(registry.toolNames().isEmpty());
    }

    @Test
    @DisplayName("同一工具归两个包＝合法并存，两个包都在清单里")
    void sharedToolAcrossPackagesIsAllowed() {
        AgentCapabilityPackageRegistry registry = new AgentCapabilityPackageRegistry(Arrays.asList(
                pkg("read-doc", EnumSet.of(CapabilityTag.READ), "readDoc"),
                pkg("write-doc", EnumSet.of(CapabilityTag.WRITE), "readDoc", "writeDocument")));

        assertEquals(2, registry.all().size());
        assertEquals(2, registry.toolNames().size());
        assertTrue(registry.toolNames().contains("readDoc"));
    }

    @Test
    @DisplayName("of() 做不可变拷贝：登记件构造后改数组不影响清单")
    void declaredToolsAreImmutableSnapshot() {
        String[] tools = {"submitPlan"};
        AgentCapabilityPackage pkg = AgentCapabilityPackage.of("plan", "计划", "test", null,
                EnumSet.of(CapabilityTag.SESSION), tools);
        tools[0] = "hacked";
        List<AgentCapabilityPackage> packages = Collections.singletonList(pkg);
        AgentCapabilityPackageRegistry registry = new AgentCapabilityPackageRegistry(packages);
        assertEquals(Collections.singletonList("submitPlan"), registry.all().get(0).getTools());
        assertThrows(UnsupportedOperationException.class, () -> pkg.tools().add("x"));
    }
}
