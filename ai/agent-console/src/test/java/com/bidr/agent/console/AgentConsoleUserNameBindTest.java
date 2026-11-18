package com.bidr.agent.console;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bidr.agent.console.dao.entity.AgentDefinition;
import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.entity.SkillAsset;
import com.bidr.agent.console.dao.repository.AgentManifestService;
import com.bidr.agent.console.dto.AgentDefinitionView;
import com.bidr.agent.console.dto.AgentManifestView;
import com.bidr.agent.console.dto.AgentSelectionCmd;
import com.bidr.agent.console.dto.SkillAssetView;
import com.bidr.agent.console.selection.AgentDefinitionAdminService;
import com.bidr.agent.console.skill.SystemSkillAdminService;
import com.bidr.authorization.bind.annotation.BindUser;
import com.bidr.authorization.bind.annotation.UserRef;
import com.bidr.kernel.config.response.BindRepo;
import com.bidr.kernel.config.response.BindRepoHandler;
import com.bidr.kernel.config.response.BindRepoResolver;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import com.bidr.llm.agent.OperatorResolver;
import com.bidr.llm.agent.conversation.AgentConversationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Title: AgentConsoleUserNameBindTest
 * Description: 管理页把工号翻成姓名的读侧契约（M14 棒 2 增量补 ＋ 棒 6 同族）。钉四条：
 * ① 每个把视图还给页面的端点都翻姓名（清单／单条／保存三腿同形），且 <b>工号列一律原样保留</b>——装配单的 asked_by 是原文展开权限的比对依据，
 * 换成姓名就等于把闸拆了；勾选行的 update_by 是审计列，同理不许动；② 查不到的工号与空工号都留空，
 * 页面按"姓名∨工号"回落，不许编造；③ 行 → 视图（{@code of}）不查库，派生列只由端点回填；
 * ④ {@code @BindUser} 的靶子钉死：T2.3c 起 {@code entity()} 声明占位实体 {@link UserRef}（不再直耦合
 * AcUser），运行时经 {@code BindRepoResolver} SPI 解析——server 侧 {@code DefaultBindRepoResolver}
 * 默认解析为 {@code AcUser}（工号列 customer_number，取 name）。本测试按同语义注册桩解析器
 * UserRef→(桩 repo, 桩实体 {@link StubAcUser}：D-1 依赖收窄后本模块不再引 authorization(server)，
 * 桩实体映射 ac_user 的 customer_number/`name` 逐字同形、valid 走驼峰 fallback（桩 repo 为 mock 不落库，列名差异无行为面）)，行为腿走真实 SPI 兜底通道；
 * 元数据腿钉占位声明本身。改注解必先过这条腿。
 * 全程 mock Spring 上下文与 BaseSqlRepo，不连库。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class AgentConsoleUserNameBindTest {

    private static final String ADMIN_NUMBER = "000001";
    private static final String ADMIN_NAME = "系统管理员";

    /**
     * 🔴 {@code RespConvert} 的 BindRepoHandler 是进程内双检锁缓存，故上下文只在全部腿之前种一次、
     * 全程共用一份桩——每条腿各换 ctx 会让后跑的腿吃到先跑那条的索引。
     * <p>T2.3c 后 @BindUser 声明占位实体 UserRef（repoIndex 无命中），必须再种一个
     * {@link BindRepoResolver} 桩走 SPI 兜底通道——与 server 侧 DefaultBindRepoResolver
     * （UserRef→AcUser）同语义，行为腿因此仍验证「工号→ac_user.customer_number→name」全链。</p>
     */
    @BeforeAll
    @SuppressWarnings("rawtypes")
    static void stubAcUserRepo() {
        BaseSqlRepo acUserRepo = Mockito.mock(BaseSqlRepo.class);
        Mockito.when(acUserRepo.getEntityClass()).thenReturn(StubAcUser.class);
        Mockito.when(acUserRepo.select(Mockito.any(Wrapper.class)))
                .thenReturn(Arrays.asList(acUser(ADMIN_NUMBER, ADMIN_NAME), acUser("000002", "张三")));

        BindRepoResolver userRefResolver = entityClass -> UserRef.class.equals(entityClass)
                ? Optional.of(new BindRepoResolver.BindTarget(acUserRepo, StubAcUser.class))
                : Optional.empty();

        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForType(BindRepoHandler.class)).thenReturn(new String[]{"bindRepoHandler"});
        Mockito.when(ctx.getBean("bindRepoHandler")).thenReturn(new BindRepoHandler());
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class)).thenReturn(new String[]{"acUserRepo"});
        Mockito.when(ctx.getBean("acUserRepo")).thenReturn(acUserRepo);
        Mockito.when(ctx.getBeanNamesForType(BindRepoResolver.class)).thenReturn(new String[]{"userRefBindResolver"});
        Mockito.when(ctx.getBean("userRefBindResolver")).thenReturn(userRefResolver);
        BeanUtil.setContext(ctx);
    }

    @AfterAll
    static void clearContext() {
        BeanUtil.setContext(null);
    }

    private final AgentManifestService repo = Mockito.mock(AgentManifestService.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<AgentConversationService> conversationProvider = Mockito.mock(ObjectProvider.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<OperatorResolver> operatorProvider = Mockito.mock(ObjectProvider.class);

    private final AgentManifestController controller =
            new AgentManifestController(repo, conversationProvider, operatorProvider);

    private final AgentDefinitionAdminService adminService = Mockito.mock(AgentDefinitionAdminService.class);

    private final AgentDefinitionAdminController definitionController =
            new AgentDefinitionAdminController(adminService);

    private final SystemSkillAdminService skillAdminService = Mockito.mock(SystemSkillAdminService.class);

    private final SystemSkillAdminController skillController =
            new SystemSkillAdminController(skillAdminService);

    @Test
    @DisplayName("列表读腿：一批工号一次查库翻姓名，工号列原样、查不到与空值都不编造")
    void recentLegFillsNamesWithoutTouchingNumber() {
        Mockito.when(repo.listRecent(null, 20)).thenReturn(Arrays.asList(
                row("s-1", ADMIN_NUMBER), row("s-2", "888888"), row("s-3", null)));

        List<AgentManifestView> views = controller.recent(null, null);

        assertEquals(ADMIN_NAME, views.get(0).getAskedByName());
        assertEquals(ADMIN_NUMBER, views.get(0).getAskedBy());
        assertNull(views.get(1).getAskedByName());
        assertEquals("888888", views.get(1).getAskedBy());
        assertNull(views.get(2).getAskedByName());
        assertNull(views.get(2).getAskedBy());
    }

    @Test
    @DisplayName("单条读腿：不带 include 也回姓名，且不碰原文展开闸")
    void singleLegFillsNameWithoutInclude() {
        Mockito.when(repo.findBySession("s-1")).thenReturn(row("s-1", ADMIN_NUMBER));

        AgentManifestView view = controller.bySession("s-1", null);

        assertEquals(ADMIN_NAME, view.getAskedByName());
        assertEquals(ADMIN_NUMBER, view.getAskedBy());
        Mockito.verifyNoInteractions(operatorProvider);
    }

    @Test
    @DisplayName("行 → 视图不查库：派生列由端点回填")
    void ofDoesNotResolveName() {
        AgentManifestView view = AgentManifestView.of(row("s-1", ADMIN_NUMBER));

        assertEquals(ADMIN_NUMBER, view.getAskedBy());
        assertNull(view.getAskedByName());
    }

    @Test
    @DisplayName("@BindUser 靶子钉死：asked_by → UserRef 占位（SPI 解析 AcUser.customer_number），取 name")
    void bindAnnotationPointsAtAcUserName() throws Exception {
        Field field = AgentManifestView.class.getDeclaredField("askedByName");
        assertNotNull(field.getAnnotation(BindUser.class));

        BindRepo merged = AnnotatedElementUtils.findMergedAnnotation(field, BindRepo.class);
        assertNotNull(merged);
        // T2.3c 起 entity() 声明占位实体 UserRef（解绑 AcUser）；有效实体由 SPI 运行时解析（本测试
        // 桩＝server 侧 DefaultBindRepoResolver 语义：UserRef→AcUser，行为腿覆盖该链路）
        assertEquals(UserRef.class, merged.entity());
        assertEquals("customerNumber", merged.matchField());
        assertEquals("askedBy", merged.sourceField());
        assertEquals("name", merged.extractField());
    }

    // ---------------- 勾选页「最后改动」同族两腿 ----------------

    @Test
    @DisplayName("勾选清单读腿：改勾选人的工号翻姓名，update_by 原样、无行不编造")
    void definitionListLegFillsUpdaterNameWithoutTouchingNumber() {
        Mockito.when(adminService.list()).thenReturn(Arrays.asList(
                AgentDefinitionView.of("flow:ask", definitionRow(ADMIN_NUMBER)),
                AgentDefinitionView.of("888888-holder", definitionRow("888888")),
                AgentDefinitionView.of("never-ticked", null)));

        List<AgentDefinitionView> views = definitionController.list();

        assertEquals(ADMIN_NAME, views.get(0).getUpdateByName());
        assertEquals(ADMIN_NUMBER, views.get(0).getUpdateBy());
        assertNull(views.get(1).getUpdateByName());
        assertEquals("888888", views.get(1).getUpdateBy());
        assertNull(views.get(2).getUpdateByName());
        assertNull(views.get(2).getUpdateBy());
    }

    @Test
    @DisplayName("单个 agent 读腿同样回姓名；行 → 视图本身不查库")
    void definitionSingleLegFillsNameAndOfStaysPure() {
        Mockito.when(adminService.get("flow:ask")).thenReturn(AgentDefinitionView.of("flow:ask", definitionRow(ADMIN_NUMBER)));

        assertEquals(ADMIN_NAME, definitionController.get("flow:ask").getUpdateByName());

        AgentDefinitionView pure = AgentDefinitionView.of("flow:ask", definitionRow(ADMIN_NUMBER));
        assertEquals(ADMIN_NUMBER, pure.getUpdateBy());
        assertNull(pure.getUpdateByName());
    }

    @Test
    @DisplayName("保存腿同形：响应里也带姓名（与清单／单条一致，页面无需靠重读列表才出姓名）")
    void definitionSaveLegFillsNameSameAsReadLegs() {
        Mockito.when(adminService.save("flow:ask", new AgentSelectionCmd()))
                .thenReturn(AgentDefinitionView.of("flow:ask", definitionRow(ADMIN_NUMBER)));

        AgentDefinitionView saved = definitionController.save("flow:ask", new AgentSelectionCmd());

        assertEquals(ADMIN_NAME, saved.getUpdateByName());
        assertEquals(ADMIN_NUMBER, saved.getUpdateBy());
    }

    @Test
    @DisplayName("@BindUser 靶子钉死（勾选行）：update_by → UserRef 占位（SPI 解析 AcUser），取 name")
    void definitionBindAnnotationPointsAtAcUserName() throws Exception {
        Field field = AgentDefinitionView.class.getDeclaredField("updateByName");
        assertNotNull(field.getAnnotation(BindUser.class));

        BindRepo merged = AnnotatedElementUtils.findMergedAnnotation(field, BindRepo.class);
        assertNotNull(merged);
        // T2.3c 占位语义（同 askedByName 腿注释）
        assertEquals(UserRef.class, merged.entity());
        assertEquals("customerNumber", merged.matchField());
        assertEquals("updateBy", merged.sourceField());
        assertEquals("name", merged.extractField());
    }

    // ---------------- 系统技能包台账「登记人」同族三腿 ----------------

    @Test
    @DisplayName("台账读腿：登记人工号翻姓名，create_by 原样、对账线程写的空工号不编造")
    void skillAssetLegFillsRegisterNameWithoutTouchingNumber() {
        Mockito.when(skillAdminService.listAssets()).thenReturn(Arrays.asList(
                skillAsset(ADMIN_NUMBER), skillAsset("888888"), skillAsset(null)));

        List<SkillAssetView> views = skillController.assets();

        assertEquals(ADMIN_NAME, views.get(0).getCreateByName());
        assertEquals(ADMIN_NUMBER, views.get(0).getCreateBy());
        assertEquals("doc-writing", views.get(0).getSkillName());
        assertNull(views.get(1).getCreateByName());
        assertEquals("888888", views.get(1).getCreateBy());
        assertNull(views.get(2).getCreateByName());
        assertNull(views.get(2).getCreateBy());
    }

    @Test
    @DisplayName("上传腿同样回姓名；台账行 → 视图本身不查库，也不下发存储键")
    void skillUploadLegFillsNameAndOfStaysPure() throws Exception {
        MultipartFile file = Mockito.mock(MultipartFile.class);
        Mockito.when(file.getBytes()).thenReturn(new byte[] { 1 });
        Mockito.when(skillAdminService.upload(Mockito.any(byte[].class))).thenReturn(skillAsset(ADMIN_NUMBER));

        assertEquals(ADMIN_NAME, skillController.upload(file).getCreateByName());

        SkillAssetView pure = SkillAssetView.of(skillAsset(ADMIN_NUMBER));
        assertEquals(ADMIN_NUMBER, pure.getCreateBy());
        assertNull(pure.getCreateByName());
        // 负向腿：读视图不得有存储对象键与软删列——实体是从 DDL 出来的，视图露一寸就收不回来
        assertTrue(Arrays.stream(SkillAssetView.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equals("packageKey") || f.getName().equals("valid")));
    }

    @Test
    @DisplayName("@BindUser 靶子钉死（台账行）：create_by → UserRef 占位（SPI 解析 AcUser），取 name")
    void skillBindAnnotationPointsAtAcUserName() throws Exception {
        Field field = SkillAssetView.class.getDeclaredField("createByName");
        assertNotNull(field.getAnnotation(BindUser.class));

        BindRepo merged = AnnotatedElementUtils.findMergedAnnotation(field, BindRepo.class);
        assertNotNull(merged);
        // T2.3c 占位语义（同 askedByName 腿注释）
        assertEquals(UserRef.class, merged.entity());
        assertEquals("customerNumber", merged.matchField());
        assertEquals("createBy", merged.sourceField());
        assertEquals("name", merged.extractField());
    }

    // ---------------- 脚手架 ----------------

    private static SkillAsset skillAsset(String createBy) {
        SkillAsset row = new SkillAsset();
        row.setId(7L);
        row.setSkillName("doc-writing");
        row.setStatus("PUBLISHED");
        row.setOrigin("UPLOADED");
        row.setPackageKey("agent-console/skill/doc-writing.zip");
        row.setCreateBy(createBy);
        return row;
    }

    private static AgentDefinition definitionRow(String updateBy) {
        AgentDefinition row = new AgentDefinition();
        row.setAgentKey("flow:ask");
        row.setCapabilityPackages("[\"plan\"]");
        row.setUpdateBy(updateBy);
        return row;
    }

    private static StubAcUser acUser(String customerNumber, String name) {
        StubAcUser user = new StubAcUser();
        user.setCustomerNumber(customerNumber);
        user.setName(name);
        user.setValid("1");
        return user;
    }

    /**
     * ac_user 只读桩实体（D-1 依赖收窄）：替代原先测试直引的 authorization(server) AcUser，
     * 列映射同形（customer_number / `name` / valid）——BindRepoHandler 对有效实体只做反射读
     * （@TableField 列名解析 + valid 存在性判定 + 行 getter），桩 repo 本身 mock，SQL 不落库。
     */
    @TableName(value = "ac_user")
    @lombok.Data
    public static class StubAcUser {

        @TableField(value = "customer_number")
        private String customerNumber;

        @TableField(value = "`name`")
        private String name;

        private String valid;
    }

    private static AgentManifest row(String sessionId, String askedBy) {
        AgentManifest row = new AgentManifest();
        row.setSessionId(sessionId);
        row.setAgentKey("inkhub-doc-writer");
        row.setDisplayName("文档写作");
        row.setAskedBy(askedBy);
        row.setToolCount(1);
        row.setToolNames("[\"echo\"]");
        return row;
    }
}
