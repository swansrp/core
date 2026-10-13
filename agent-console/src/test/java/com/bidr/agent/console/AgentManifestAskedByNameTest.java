package com.bidr.agent.console;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bidr.agent.console.dao.entity.AgentManifest;
import com.bidr.agent.console.dao.repository.AgentManifestService;
import com.bidr.agent.console.dto.AgentManifestView;
import com.bidr.authorization.bind.annotation.BindUser;
import com.bidr.authorization.dao.entity.AcUser;
import com.bidr.kernel.config.response.BindRepo;
import com.bidr.kernel.config.response.BindRepoHandler;
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Title: AgentManifestAskedByNameTest
 * Description: 装配单发起人姓名的读侧契约（M14 棒 2 增量）。钉四条：
 * ① 两条读腿都把工号翻成姓名，且 <b>asked_by 一律原样保留</b>——它是原文展开权限的比对依据，
 * 换成姓名就等于把闸拆了；② 查不到的工号与空工号都留空，页面按"姓名∨工号"回落，不许编造；
 * ③ 行 → 视图（{@code of}）不查库，派生列只由端点回填；④ {@code @BindUser} 的靶子钉死
 * （asked_by → ac_user.customer_number，取 name），改注解必先过这条腿。
 * 全程 mock Spring 上下文与 BaseSqlRepo，不连库。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class AgentManifestAskedByNameTest {

    private static final String ADMIN_NUMBER = "000001";
    private static final String ADMIN_NAME = "系统管理员";

    /**
     * 🔴 {@code RespConvert} 的 BindRepoHandler 是进程内双检锁缓存，故上下文只在全部腿之前种一次、
     * 全程共用一份桩——每条腿各换 ctx 会让后跑的腿吃到先跑那条的索引。
     */
    @BeforeAll
    @SuppressWarnings("rawtypes")
    static void stubAcUserRepo() {
        BaseSqlRepo acUserRepo = Mockito.mock(BaseSqlRepo.class);
        Mockito.when(acUserRepo.getEntityClass()).thenReturn(AcUser.class);
        Mockito.when(acUserRepo.select(Mockito.any(Wrapper.class)))
                .thenReturn(Arrays.asList(acUser(ADMIN_NUMBER, ADMIN_NAME), acUser("000002", "张三")));

        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForType(BindRepoHandler.class)).thenReturn(new String[]{"bindRepoHandler"});
        Mockito.when(ctx.getBean("bindRepoHandler")).thenReturn(new BindRepoHandler());
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class)).thenReturn(new String[]{"acUserRepo"});
        Mockito.when(ctx.getBean("acUserRepo")).thenReturn(acUserRepo);
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
    @DisplayName("@BindUser 靶子钉死：asked_by → ac_user.customer_number，取 name")
    void bindAnnotationPointsAtAcUserName() throws Exception {
        Field field = AgentManifestView.class.getDeclaredField("askedByName");
        assertNotNull(field.getAnnotation(BindUser.class));

        BindRepo merged = AnnotatedElementUtils.findMergedAnnotation(field, BindRepo.class);
        assertNotNull(merged);
        assertEquals(AcUser.class, merged.entity());
        assertEquals("customerNumber", merged.matchField());
        assertEquals("askedBy", merged.sourceField());
        assertEquals("name", merged.extractField());
    }

    // ---------------- 脚手架 ----------------

    private static AcUser acUser(String customerNumber, String name) {
        AcUser user = new AcUser();
        user.setCustomerNumber(customerNumber);
        user.setName(name);
        user.setValid("1");
        return user;
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
