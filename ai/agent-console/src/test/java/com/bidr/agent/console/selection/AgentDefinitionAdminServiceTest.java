package com.bidr.agent.console.selection;

import com.bidr.agent.console.capability.AgentCapabilityPackage;
import com.bidr.agent.console.capability.AgentCapabilityPackageRegistry;
import com.bidr.agent.console.capability.CapabilityTag;
import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.dao.repository.AgentDefinitionService;
import com.bidr.agent.console.dto.AgentDefinitionView;
import com.bidr.agent.console.dto.AgentSelectionCmd;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.agent.registry.AgentDescriptor;
import com.bidr.llm.agent.registry.AgentKind;
import com.bidr.llm.agent.registry.AgentRegistryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: AgentDefinitionAdminServiceTest
 * Description: agent 勾选读写面单测（M14 棒6-E）。逐条钉住裁定：
 * <ul>
 *   <li>🔴 <b>闸门 fail closed</b>：真实例（不覆写 {@code isPlatformAdmin()}）三条腿全拒，
 *       且 {@code verifyNoInteractions} 仓储与注册表——判不出管理员时连读都不该碰库；
 *       permitService 取不到＝同一条拒绝；</li>
 *   <li>拒绝一律 {@link ServiceException}（非零码）：NoticeException 在前端落 code=0，
 *       管理员会以为勾上了；</li>
 *   <li>清单以注册表为主序，历史行（注册表查不到）落末尾且 {@code registered=false}；</li>
 *   <li>存在性以注册表为正源：未注册且库里无行＝拒绝新建（拼错的键会落成永不生效的配置），
 *       但<b>已有历史行允许改写</b>（清空勾选本身就是一次写，也是唯一的回退动作）；</li>
 *   <li>🔴 <b>只有能力包那一栏校验包名</b>（唯一被运行期读走的一栏），其余四栏只登记不校验；</li>
 *   <li>空白项当场拒——放过去会被 {@link SelectionColumn} 丢成 {@code []}＝静默关停；</li>
 *   <li>整组覆盖：五栏一次给齐，null→NULL、[]→"[]"、非空→去重去空白后的 JSON；operator 由
 *       OperatorResolver 现读，取不到只落空 update_by、不影响写入。</li>
 * </ul>
 * 全程 mock 仓储与注册表，不连库。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class AgentDefinitionAdminServiceTest {

    private static final String WRITER = "inkhub-doc-writer";

    private static final String KNOWLEDGE_QA = "inkhub-knowledge-qa";

    private static final String RETIRED = "inkhub-old-agent";

    private AgentDefinitionService definitions;

    private AgentCapabilityPackageRegistry packages;

    private AgentRegistryService agentRegistry;

    private ObjectProvider<OperatorResolver> operatorResolver;

    private AgentDefinitionAdminService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        definitions = Mockito.mock(AgentDefinitionService.class);
        packages = new AgentCapabilityPackageRegistry(Arrays.<AgentCapabilityPackage>asList(
                AgentCapabilityPackage.of("plan", "计划台账", "llm", null,
                        EnumSet.of(CapabilityTag.SESSION), "submitPlan"),
                AgentCapabilityPackage.of("ask-user", "向用户提问", "llm", null,
                        EnumSet.of(CapabilityTag.SESSION), "askUser")));
        agentRegistry = Mockito.mock(AgentRegistryService.class);
        operatorResolver = Mockito.mock(ObjectProvider.class);
        Mockito.when(operatorResolver.getIfAvailable()).thenReturn(null);
        service = new OpenGate(definitions, packages, agentRegistry, operatorResolver);
    }

    @AfterEach
    void clearContext() {
        BeanUtil.setContext(null);
    }

    // ---------------- 闸门 ----------------

    /** 真实例＝闸门不覆写：permitService 取不到 ⇒ 按非管理员，三条腿全拒且不碰仓储 */
    @Test
    @DisplayName("🔴 闸门 fail closed：判不出管理员时读腿也不碰库，且拒绝走 ServiceException（非零码）")
    void realInstanceDeniesEverythingWithoutAdmin() {
        AgentDefinitionAdminService gated =
                new AgentDefinitionAdminService(definitions, packages, agentRegistry, operatorResolver);

        ServiceException listed = assertThrows(ServiceException.class, gated::list);
        assertThrows(ServiceException.class, () -> gated.get(WRITER));
        assertThrows(ServiceException.class, () -> gated.save(WRITER, selection(null, null, null, null, null)));

        assertTrue(listed.getMessage().contains("平台管理员"), listed.getMessage());
        Mockito.verifyNoInteractions(definitions, agentRegistry);
    }

    /** 装了 authorization 但 permitService 返 null（同 AgentUsageControllerTest 的桩法）＝同样拒绝 */
    @Test
    @DisplayName("permitService 取到 null：按无权处理")
    void nullPermitServiceIsDeny() {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean("permitService")).thenReturn(null);
        BeanUtil.setContext(ctx);

        AgentDefinitionAdminService gated =
                new AgentDefinitionAdminService(definitions, packages, agentRegistry, operatorResolver);
        assertThrows(ServiceException.class, gated::list);
        Mockito.verifyNoInteractions(definitions);
    }

    // ---------------- 读面 ----------------

    @Test
    @DisplayName("清单：注册表为主序，从未勾过的 agent 也露出来（五栏 null＝三态第一态），历史行落末尾且 registered=false")
    void listIsRegistryFirstWithStaleRowsLast() {
        Mockito.when(agentRegistry.all()).thenReturn(Arrays.asList(
                descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS),
                descriptor(KNOWLEDGE_QA, "知识问答", AgentKind.AUTONOMOUS)));
        Mockito.when(definitions.listAll()).thenReturn(Arrays.asList(
                row(WRITER, "[\"plan\"]"), row(RETIRED, "[]")));

        List<AgentDefinitionView> views = service.list();

        assertEquals(Arrays.asList(WRITER, KNOWLEDGE_QA, RETIRED), keys(views));
        AgentDefinitionView writer = views.get(0);
        assertTrue(writer.isRegistered(), WRITER + " 在注册表里＝registered=true");
        assertTrue(writer.isHasRow(), "有行要如实标 hasRow");
        assertEquals("写作 agent", writer.getDisplayName());
        assertEquals("AUTONOMOUS", writer.getKind());
        assertEquals(Collections.singletonList("plan"), writer.getCapabilityPackages());

        AgentDefinitionView qa = views.get(1);
        assertFalse(qa.isHasRow(), "无行＝hasRow=false");
        assertNull(qa.getCapabilityPackages(), "🔴 无行的五栏必须是 null（＝未启用＝放行），不许渲染成空数组");

        AgentDefinitionView stale = views.get(2);
        assertFalse(stale.isRegistered(), "库里有行但注册表查不到＝该 agent 已不在跑，要如实标出来");
        assertTrue(stale.isHasRow());
        assertEquals(Collections.emptyList(), stale.getCapabilityPackages(),
                "[] 读回来是空集合而不是 null——这一态＝登记过的全不给，与 null 那态相反");
    }

    @Test
    @DisplayName("单个读：注册表与勾选表都查不到＝拒绝；只有历史行也可读（registered=false）")
    void singleReadRejectsUnknownButKeepsStaleRow() {
        Mockito.when(agentRegistry.get("typo-key")).thenReturn(null);
        Mockito.when(definitions.findByAgentKey("typo-key")).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.get("typo-key"));
        assertTrue(e.getMessage().contains("typo-key"), e.getMessage());

        Mockito.when(agentRegistry.get(RETIRED)).thenReturn(null);
        Mockito.when(definitions.findByAgentKey(RETIRED)).thenReturn(row(RETIRED, "[]"));
        AgentDefinitionView stale = service.get(RETIRED);
        assertFalse(stale.isRegistered());
        assertEquals(Collections.emptyList(), stale.getCapabilityPackages());
    }

    // ---------------- 写面 ----------------

    @Test
    @DisplayName("🔴 未注册的键且库里无行＝拒绝新建（拼错的键会落成一串永不生效的勾选）")
    void saveRefusesNewRowForUnregisteredKey() {
        Mockito.when(agentRegistry.get(RETIRED)).thenReturn(null);
        Mockito.when(definitions.findByAgentKey(RETIRED)).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
                () -> service.save(RETIRED, selection(Collections.singletonList("plan"),
                        null, null, null, null)));
        assertTrue(e.getMessage().contains("拒绝新建"), e.getMessage());
        Mockito.verify(definitions, Mockito.never()).writeSelection(Mockito.any(), Mockito.any());
    }

    /** 已有历史行允许改写：清空勾选（回退动作）本身就是一次写 */
    @Test
    @DisplayName("注册表查不到但库里已有行＝允许改写（回退＝把列置 NULL，也是一次写）")
    void staleRowCanBeOverwritten() {
        Mockito.when(agentRegistry.get(RETIRED)).thenReturn(null);
        Mockito.when(definitions.findByAgentKey(RETIRED)).thenReturn(row(RETIRED, "[]"), row(RETIRED, null));

        AgentDefinitionView view = service.save(RETIRED, selection(null, null, null, null, null));

        ArgumentCaptor<AgentDefinition> patch = ArgumentCaptor.forClass(AgentDefinition.class);
        ArgumentCaptor<String> operator = ArgumentCaptor.forClass(String.class);
        Mockito.verify(definitions).writeSelection(patch.capture(), operator.capture());
        assertEquals(RETIRED, patch.getValue().getAgentKey());
        assertNull(patch.getValue().getCapabilityPackages(), "null 入参要写成 NULL 列＝取消启用");
        assertFalse(view.isRegistered());
        assertNull(operator.getValue(), "OperatorResolver 缺席时 update_by 落空，不影响写入");
    }

    @Test
    @DisplayName("整组覆盖：五栏各按三态落列（去重去空白、[] 原样落 []、null 落 NULL），写后回读给视图")
    void saveWritesAllFiveColumnsInThreeStates() {
        Mockito.when(agentRegistry.get(WRITER)).thenReturn(descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS));
        AgentDefinition written = row(WRITER, "[\"plan\",\"ask-user\"]");
        // 键已在注册表＝存在性那一次读库被短路掉，这条桩只服务写后回读
        Mockito.when(definitions.findByAgentKey(WRITER)).thenReturn(written);

        AgentDefinitionView view = service.save(WRITER,
                selection(Arrays.asList("plan", " plan ", "ask-user"), Collections.<String>emptyList(),
                        null, Collections.singletonList("DEFAULT"), null));

        ArgumentCaptor<AgentDefinition> patch = ArgumentCaptor.forClass(AgentDefinition.class);
        Mockito.verify(definitions).writeSelection(patch.capture(), Mockito.any());
        AgentDefinition values = patch.getValue();
        assertEquals("[\"plan\",\"ask-user\"]", values.getCapabilityPackages(), "重复与首尾空白在写侧就归一");
        assertEquals("[]", values.getSystemSkills(), "🔴 空集合要落成 []，不许落成 NULL（那是放行）");
        assertNull(values.getExternalCapabilities());
        assertEquals("[\"DEFAULT\"]", values.getModelPurpose());
        assertNull(values.getPromptSegments());
        // 回读的那一行才是页面看到的：三态按库内原文判
        assertEquals(Arrays.asList("plan", "ask-user"), view.getCapabilityPackages());
        assertEquals("写作 agent", view.getDisplayName());
        assertTrue(view.isHasRow());
    }

    /** 只读校验那一栏：其余四栏本棒只登记、没有登记表可比，按名拒人只会牵制未来的实现 */
    @Test
    @DisplayName("🔴 只有 capability_packages 校验包名；未注册的包名当场拒绝并点名")
    void onlyCapabilityPackagesAreValidated() {
        Mockito.when(agentRegistry.get(WRITER)).thenReturn(descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS));
        Mockito.when(definitions.findByAgentKey(WRITER)).thenReturn(row(WRITER, null));

        ServiceException e = assertThrows(ServiceException.class, () -> service.save(WRITER,
                selection(Arrays.asList("plan", "sandbox"), null, null, null, null)));
        assertTrue(e.getMessage().contains("未注册"), e.getMessage());
        assertTrue(e.getMessage().contains("sandbox"), "拒绝文案要点名到底是哪个包名勾不上: " + e.getMessage());
        Mockito.verify(definitions, Mockito.never()).writeSelection(Mockito.any(), Mockito.any());

        // 同一次调用里，只登记的四栏塞任意名字都放行
        service.save(WRITER, selection(Collections.singletonList("plan"),
                Collections.singletonList("某个还没上线的包名"), null, null, null));
        Mockito.verify(definitions).writeSelection(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("空白项当场拒：放过去会被丢成 []＝静默关停")
    void blankNamesAreRejected() {
        Mockito.when(agentRegistry.get(WRITER)).thenReturn(descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS));
        Mockito.when(definitions.findByAgentKey(WRITER)).thenReturn(row(WRITER, null));

        ServiceException e = assertThrows(ServiceException.class, () -> service.save(WRITER,
                selection(Collections.singletonList("  "), null, null, null, null)));
        assertTrue(e.getMessage().contains("非法名字"), e.getMessage());
        Mockito.verify(definitions, Mockito.never()).writeSelection(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("键名与请求体边界：空体、超 64 字符的 agentKey 都拒（列宽 varchar(64)，超宽会被截成查不到的键）")
    void keyAndBodyBounds() {
        Mockito.when(agentRegistry.get(WRITER)).thenReturn(descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS));

        assertThrows(ServiceException.class, () -> service.save(WRITER, null));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 65; i++) {
            tooLong.append('a');
        }
        ServiceException e = assertThrows(ServiceException.class,
                () -> service.save(tooLong.toString(), selection(null, null, null, null, null)));
        assertTrue(e.getMessage().contains("64"), e.getMessage());
        Mockito.verify(definitions, Mockito.never()).writeSelection(Mockito.any(), Mockito.any());
    }

    @Test
    @DisplayName("访问人解析失败只落空 update_by，不影响写入（写面不该因为取不到显示名而罢工）")
    void failingOperatorResolverDoesNotBlockWrite() {
        Mockito.when(agentRegistry.get(WRITER)).thenReturn(descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS));
        Mockito.when(definitions.findByAgentKey(WRITER)).thenReturn(row(WRITER, null));
        Mockito.when(operatorResolver.getIfAvailable()).thenReturn(() -> {
            throw new IllegalStateException("上下文没了");
        });

        service.save(WRITER, selection(Collections.singletonList("plan"), null, null, null, null));

        ArgumentCaptor<String> operator = ArgumentCaptor.forClass(String.class);
        Mockito.verify(definitions).writeSelection(Mockito.any(), operator.capture());
        assertNull(operator.getValue());
    }

    @Test
    @DisplayName("OperatorResolver 在位时 update_by 落当前访问人")
    void operatorIsRecordedWhenResolverPresent() {
        Mockito.when(agentRegistry.get(WRITER)).thenReturn(descriptor(WRITER, "写作 agent", AgentKind.AUTONOMOUS));
        Mockito.when(definitions.findByAgentKey(WRITER)).thenReturn(row(WRITER, null));
        Mockito.when(operatorResolver.getIfAvailable()).thenReturn(() -> "Sharp");

        service.save(WRITER, selection(Collections.singletonList("plan"), null, null, null, null));

        Mockito.verify(definitions).writeSelection(Mockito.any(), Mockito.eq("Sharp"));
    }

    // ---------------- 脚手架 ----------------

    /** 放行闸门的子类：只覆写管理员判定，其余逻辑走真实例 */
    private static class OpenGate extends AgentDefinitionAdminService {
        OpenGate(AgentDefinitionService definitions, AgentCapabilityPackageRegistry packages,
                 AgentRegistryService agentRegistry, ObjectProvider<OperatorResolver> operatorResolver) {
            super(definitions, packages, agentRegistry, operatorResolver);
        }

        @Override
        protected boolean isPlatformAdmin() {
            return true;
        }
    }

    private static AgentDescriptor descriptor(String key, String displayName, AgentKind kind) {
        AgentDescriptor descriptor = new AgentDescriptor();
        descriptor.setAgentCode(key);
        descriptor.setDisplayName(displayName);
        descriptor.setKind(kind);
        return descriptor;
    }

    private static AgentDefinition row(String agentKey, String capabilityPackages) {
        AgentDefinition row = new AgentDefinition();
        row.setAgentKey(agentKey);
        row.setCapabilityPackages(capabilityPackages);
        return row;
    }

    private static AgentSelectionCmd selection(List<String> capabilityPackages, List<String> systemSkills,
                                               List<String> externalCapabilities, List<String> modelPurpose,
                                               List<String> promptSegments) {
        AgentSelectionCmd cmd = new AgentSelectionCmd();
        cmd.setCapabilityPackages(capabilityPackages);
        cmd.setSystemSkills(systemSkills);
        cmd.setExternalCapabilities(externalCapabilities);
        cmd.setModelPurpose(modelPurpose);
        cmd.setPromptSegments(promptSegments);
        return cmd;
    }

    private static List<String> keys(List<AgentDefinitionView> views) {
        return views.stream().map(AgentDefinitionView::getAgentKey).collect(java.util.stream.Collectors.toList());
    }
}
