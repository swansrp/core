package com.bidr.agent.console.selection;

import com.bidr.agent.console.capability.AgentCapabilityPackage;
import com.bidr.agent.console.capability.AgentCapabilityPackageRegistry;
import com.bidr.agent.console.capability.CapabilityPackageDescriptor;
import com.bidr.agent.console.capability.CapabilityTag;
import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.dao.repository.AgentDefinitionService;
import dev.langchain4j.agent.tool.ToolSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentCapabilitySelectorTest
 * Description: 交集公式与验收三判据（M14 棒6）。三判据各自钉成一条可跑的断言：
 * <ul>
 *   <li><b>验收③（存量零漂移）</b>＝无行／列为 NULL／无会话身份 三条腿都返回<b>同一个清单</b>，
 *   这是"上线即不改行为"的落点；</li>
 *   <li><b>验收②（关掉某包会话手里真没有）</b>＝登记过的包未勾时其工具一律不在输出里，
 *   而<b>未登记的业务工具无条件保留</b>（"∪ 业务追加"的实现判据就是"名字在不在登记表里"）；</li>
 *   <li><b>验收①（只读档勾沙箱包勾不进）</b>＝结构化断言：只读档收窄后的清单里
 *   <b>不存在任何 WRITE／EXTERNAL_EFFECT 档的工具</b>，与勾选怎么写无关——交集算不出代码面里没有的东西。</li>
 * </ul>
 * 另外钉两件事：勾选里的<b>未注册包名按窄处理</b>（否则一次包改名＝静默扩权），
 * 以及<b>每场会话现读一次库</b>（刻意不缓存，"改完勾选多久生效"必须有确定答案）。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class AgentCapabilitySelectorTest {

    private static final String WRITER = "inkhub-doc-writer";

    private static final String KNOWLEDGE_QA = "inkhub-knowledge-qa";

    private AgentDefinitionService definitions;

    private AgentCapabilityPackageRegistry registry;

    private AgentCapabilitySelector selector;

    @BeforeEach
    void setUp() {
        registry = new AgentCapabilityPackageRegistry(Arrays.<AgentCapabilityPackage>asList(
                AgentCapabilityPackage.of("plan", "计划台账", "llm", null,
                        EnumSet.of(CapabilityTag.SESSION),
                        "submitPlan", "startPlanItem", "donePlanItem"),
                AgentCapabilityPackage.of("ask-user", "向用户提问", "llm", null,
                        EnumSet.of(CapabilityTag.SESSION), "askUser"),
                AgentCapabilityPackage.of("tool-recall", "工具结果回捞", "llm", null,
                        EnumSet.of(CapabilityTag.READ), "recallToolResult"),
                AgentCapabilityPackage.of("external-task", "外部运行时任务", "llm", null,
                        EnumSet.of(CapabilityTag.EXTERNAL_EFFECT),
                        "submitTask", "waitTask", "queryTask", "readTaskOutput", "cancelTask"),
                AgentCapabilityPackage.of("next-step", "下一步候选", "inkhub-writer", null,
                        EnumSet.of(CapabilityTag.SESSION), "suggest_next_steps")));
        definitions = Mockito.mock(AgentDefinitionService.class);
        selector = new AgentCapabilitySelector(definitions, registry);
    }

    // ---------------- 验收③：三态第一态＝放行，行为逐字不变 ----------------

    @Test
    @DisplayName("验收③：表里没行＝清单原样返回（存量 agent 一行都没有）")
    void noRowMeansNoChange() {
        Mockito.when(definitions.findByAgentKey(WRITER)).thenReturn(null);
        List<ToolSpecification> writer = writerSurface();

        assertEquals(names(writer), names(selector.restrict(WRITER, writer)));
    }

    @Test
    @DisplayName("验收③：列为 NULL＝未启用勾选＝清单原样（含顺序）")
    void nullColumnMeansNoChange() {
        stub(WRITER, null);
        List<ToolSpecification> writer = writerSurface();

        List<ToolSpecification> out = selector.restrict(WRITER, writer);
        assertEquals(names(writer), names(out));
        assertEquals(writer, out, "未启用时连顺序都不该动");
    }

    @Test
    @DisplayName("无会话身份＝不过滤，且一次库都不查")
    void blankAgentKeySkipsDbEntirely() {
        List<ToolSpecification> writer = writerSurface();

        assertEquals(names(writer), names(selector.restrict(null, writer)));
        assertEquals(names(writer), names(selector.restrict("   ", writer)));
        Mockito.verify(definitions, Mockito.never()).findByAgentKey(Mockito.anyString());
    }

    // ---------------- 验收②：勾掉＝手里真没有 ----------------

    @Test
    @DisplayName("验收②：勾 plan 后 ask-user 与外部任务档的工具都不在清单里")
    void onlySelectedPackagesSurvive() {
        stub(WRITER, "[\"plan\"]");

        List<String> kept = names(selector.restrict(WRITER, writerSurface()));

        assertTrue(kept.containsAll(Arrays.asList("submitPlan", "startPlanItem", "donePlanItem")), kept.toString());
        for (String gone : Arrays.asList("askUser", "suggest_next_steps", "submitTask", "waitTask")) {
            assertFalse(kept.contains(gone), "未勾的登记面工具该被摘掉：" + gone);
        }
    }

    /** 🔴 "∪ 业务追加"的实现判据：名字不在登记表里＝框架不认识＝勾选无权关它 */
    @Test
    @DisplayName("勾零个＝登记过的全不给，但业务追加的工具一只不少")
    void emptySelectionClosesRegisteredFaceOnly() {
        stub(WRITER, "[]");

        List<String> kept = names(selector.restrict(WRITER, writerSurface()));

        assertEquals(Arrays.asList("searchKnowledge", "writeDraftSegment"), kept,
                "业务件（未登记）无条件保留，登记件全关");
        assertEquals(0, count(kept, registry.toolNames()), "登记面里一只都不该留");
    }

    @Test
    @DisplayName("🔴 只减不加：输出恒为输入的子集；勾满＝回到改前清单")
    void restrictionIsAlwaysAnOrderedSubset() {
        stub(WRITER, "[\"plan\",\"ask-user\",\"tool-recall\",\"external-task\",\"next-step\"]");
        List<ToolSpecification> writer = writerSurface();

        List<String> kept = names(selector.restrict(WRITER, writer));

        assertTrue(writer.stream().allMatch(s -> kept.contains(s.name())), "出现的名字必须都在输入里");
        assertEquals(names(writer), kept, "把登记面全勾上＝与不勾一样（勾选只能减，减到零才有效果）");
    }

    // ---------------- 验收①：只读档勾沙箱包勾不进 ----------------

    @Test
    @DisplayName("验收①：knowledge-qa 档勾 external-task＋plan＝交集为空，清单与改前一致")
    void readOnlyFeatureCannotBeWidenedBySelection() {
        stub(KNOWLEDGE_QA, "[\"external-task\",\"plan\"]");
        List<ToolSpecification> qa = knowledgeQaSurface();

        List<String> kept = names(selector.restrict(KNOWLEDGE_QA, qa));

        assertEquals(names(qa), kept, "feature 面里没有的东西，勾选变不出来（交集＝空）");
    }

    /** 同一条判据的结构化版本：不看勾了什么，只看结果里绝不该有副作用档工具 */
    @Test
    @DisplayName("验收①（结构化）：只读档收窄后的清单里不存在任何 WRITE／EXTERNAL_EFFECT 工具")
    void noEffectBearingToolEverAppearsInReadOnlySurface() {
        for (String selection : Arrays.asList("[\"external-task\"]", "[\"external-task\",\"plan\"]",
                "[\"plan\",\"ask-user\",\"next-step\"]")) {
            stub(KNOWLEDGE_QA, selection);
            List<String> kept = names(selector.restrict(KNOWLEDGE_QA, knowledgeQaSurface()));
            for (CapabilityPackageDescriptor descriptor : registry.all()) {
                boolean effectBearing = descriptor.getTags().contains(CapabilityTag.EXTERNAL_EFFECT.name())
                        || descriptor.getTags().contains(CapabilityTag.WRITE.name());
                if (!effectBearing) {
                    continue;
                }
                for (String tool : descriptor.getTools()) {
                    assertFalse(kept.contains(tool),
                            "勾选=" + selection + " 时只读档出现了副作用档工具 " + tool);
                }
            }
        }
    }

    // ---------------- 脏数据方向 ----------------

    @Test
    @DisplayName("勾选里有未注册的包名＝该名字不贡献任何工具（按窄），不因此整体放行")
    void unknownPackageContributesNothing() {
        stub(WRITER, "[\"no-such-package\",\"plan\"]");

        List<String> kept = names(selector.restrict(WRITER, writerSurface()));

        assertTrue(kept.contains("submitPlan"));
        assertFalse(kept.contains("askUser"), "脏名字不能变成\"看不见就算全给\"");
    }

    @Test
    @DisplayName("列里是坏 JSON＝按\"一个都不给\"处理（宁可当场不能干活，也不静默扩权）")
    void corruptColumnClosesRegisteredFace() {
        stub(WRITER, "{\"这个形状根本不是数组\"");

        List<String> kept = names(selector.restrict(WRITER, writerSurface()));

        assertEquals(Arrays.asList("searchKnowledge", "writeDraftSegment"), kept);
    }

    /** 引擎追加件（recallToolResult）也在登记面里，所以平台勾得到它——这条防"框架自己加的工具勾不掉" */
    @Test
    @DisplayName("引擎追加的回捞工具同样受勾选支配")
    void engineAppendedRecallToolIsAlsoSelectable() {
        stub(WRITER, "[\"plan\",\"tool-recall\"]");
        List<ToolSpecification> withRecall = new ArrayList<>(writerSurface());
        withRecall.add(spec("recallToolResult"));

        assertTrue(names(selector.restrict(WRITER, withRecall)).contains("recallToolResult"),
                "勾了 tool-recall 就该留着");

        stub(WRITER, "[\"plan\"]");
        assertFalse(names(selector.restrict(WRITER, Arrays.asList(
                spec("searchKnowledge"), spec("recallToolResult")))).contains("recallToolResult"),
                "只勾 plan 时回捞工具被摘（半开态由框架侧 warn，见 ToolAgentRunner 那条腿）");
    }

    // ---------------- 生效时机 ----------------

    @Test
    @DisplayName("每场会话现读一次库：不缓存，改完勾选下一场立刻生效")
    void readsTheRowEverySessionNoCache() {
        stub(WRITER, "[\"plan\"]");
        List<ToolSpecification> writer = writerSurface();

        assertTrue(names(selector.restrict(WRITER, writer)).contains("submitPlan"));
        assertTrue(names(selector.restrict(WRITER, writer)).contains("submitPlan"));

        Mockito.verify(definitions, Mockito.times(2)).findByAgentKey(WRITER);
    }

    // ---------------- 脚手架 ----------------

    private void stub(String agentKey, String capabilityPackages) {
        AgentDefinition definition = new AgentDefinition();
        definition.setAgentKey(agentKey);
        definition.setCapabilityPackages(capabilityPackages);
        Mockito.when(definitions.findByAgentKey(agentKey)).thenReturn(definition);
    }

    /** 写作档（BOTH）：业务只读＋写件，加登记面里的 plan / ask-user / next-step / external-task */
    private static List<ToolSpecification> writerSurface() {
        return new ArrayList<>(Arrays.asList(
                spec("searchKnowledge"), spec("writeDraftSegment"),
                spec("submitPlan"), spec("startPlanItem"), spec("donePlanItem"),
                spec("askUser"), spec("suggest_next_steps"),
                spec("submitTask"), spec("waitTask")));
    }

    /** 只读问答档：代码面里就没有写件与沙箱件（feature 面是结构性上限），三只全是未登记的业务工具 */
    private static List<ToolSpecification> knowledgeQaSurface() {
        return new ArrayList<>(Arrays.asList(
                spec("searchKnowledge"), spec("readFile"), spec("readOutline")));
    }

    private static ToolSpecification spec(String name) {
        return ToolSpecification.builder().name(name).build();
    }

    private static List<String> names(List<ToolSpecification> specs) {
        List<String> out = new ArrayList<>();
        for (ToolSpecification spec : specs) {
            out.add(spec == null ? null : spec.name());
        }
        return out;
    }

    /** kept 与比对集的交集大小（用来数"登记面里还剩几只"） */
    private static int count(List<String> kept, Collection<String> candidates) {
        int n = 0;
        for (String candidate : candidates) {
            if (kept.contains(candidate)) {
                n++;
            }
        }
        return n;
    }
}
