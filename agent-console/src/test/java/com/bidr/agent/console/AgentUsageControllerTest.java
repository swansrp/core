package com.bidr.agent.console;

import com.bidr.agent.console.dao.entity.ModelUsage;
import com.bidr.agent.console.dao.repository.ModelUsageService;
import com.bidr.agent.console.dto.ModelUsageSummary;
import com.bidr.kernel.exception.NoticeException;
import com.bidr.kernel.utils.BeanUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Title: AgentUsageControllerTest
 * Description: 用量统计读腿契约测试（M14 棒5）：① 🔴 两条读腿都要过平台管理员闸，
 * 且 permitService 取不到时按<b>无权</b>处理（这条读腿跨空间、还带 asked_by／session_id，
 * "取不到 bean 就放过"等于把使用画像开放给所有登录用户）；② 时间窗口径＝按日左闭右开，
 * {@code to} 含当天（右界取次日零点），缺省最近 7 天含今日；③ 行数上限夹住，不被调用方拉穿。
 * 全程 mock 仓储与 Spring 上下文，不连库。
 *
 * @author Sharp
 * @since 2026-10-13
 */
class AgentUsageControllerTest {

    private final ModelUsageService repo = Mockito.mock(ModelUsageService.class);

    private final AgentUsageController controller = new AgentUsageController(repo);

    @AfterEach
    void clearContext() {
        BeanUtil.setContext(null);
    }

    @Test
    @DisplayName("非管理员读聚合：拒绝，且不碰仓储")
    void summaryRejectsNonAdmin() {
        stubPermit(false);
        NoticeException e = assertThrows(NoticeException.class,
                () -> controller.summary(null, null, null, null));
        assertTrue(e.getMessage().contains("平台管理员"), e.getMessage());
        Mockito.verifyNoInteractions(repo);
    }

    /** bean 缺席的工程形态（没装 authorization）：宁可报错，不能因为"判不出管理员"就放行 */
    @Test
    @DisplayName("permitService 取不到：按无权处理")
    void missingPermitServiceIsDeny() {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean("permitService")).thenReturn(null);
        BeanUtil.setContext(ctx);

        assertThrows(NoticeException.class, () -> controller.summary(null, null, null, null));
        assertThrows(NoticeException.class, () -> controller.bySession("s-1", null));
        Mockito.verifyNoInteractions(repo);
    }

    @Test
    @DisplayName("缺省窗口＝最近 7 天含今日（起于 6 天前零点、止于明日零点）")
    void defaultWindowIsSevenDaysIncludingToday() {
        stubPermit(true);
        Mockito.when(repo.summarize(Mockito.any(Date.class), Mockito.any(Date.class),
                Mockito.any(), Mockito.any())).thenReturn(Arrays.asList(new ModelUsageSummary()));

        List<ModelUsageSummary> rows = controller.summary(null, null, null, null);
        assertEquals(1, rows.size());

        ArgumentCaptor<Date> from = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> to = ArgumentCaptor.forClass(Date.class);
        Mockito.verify(repo).summarize(from.capture(), to.capture(), Mockito.eq(null), Mockito.eq(null));
        assertEquals(dayOffset(-6), from.getValue());
        assertEquals(dayOffset(1), to.getValue());
    }

    @Test
    @DisplayName("to 含当天：右开界取次日零点；筛选参数原样透传")
    void endDayIsInclusiveAndFiltersPassThrough() {
        stubPermit(true);
        Date from = dayOffset(-2);
        Date to = dayOffset(0);

        controller.summary(from, to, "inkhub-check", "AGENT");

        Mockito.verify(repo).summarize(Mockito.eq(from), Mockito.eq(dayOffsetAfter(to)),
                Mockito.eq("inkhub-check"), Mockito.eq("AGENT"));
    }

    @Test
    @DisplayName("会话流水：limit 缺省 100、超上限夹到 500、非法值回落缺省")
    void sessionLimitIsClamped() {
        stubPermit(true);
        Mockito.when(repo.listBySession(Mockito.eq("s-1"), Mockito.anyInt()))
                .thenReturn(Arrays.asList(new ModelUsage()));

        controller.bySession("s-1", null);
        controller.bySession("s-1", 99999);
        controller.bySession("s-1", 0);

        ArgumentCaptor<Integer> limit = ArgumentCaptor.forClass(Integer.class);
        Mockito.verify(repo, Mockito.times(3)).listBySession(Mockito.eq("s-1"), limit.capture());
        assertEquals(Arrays.asList(100, 500, 100), limit.getAllValues());
    }

    // ---------------- 脚手架 ----------------

    /** 把 permitService 桩进 BeanUtil 的按名取 bean 路径（控制器用的是反射调用，同口径） */
    private void stubPermit(boolean admin) {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBean("permitService")).thenReturn(new StubPermitService(admin));
        BeanUtil.setContext(ctx);
    }

    /** 只带 isAdmin() 的最小桩：控制器的反射调用只认这个方法名 */
    public static class StubPermitService {
        private final boolean admin;

        public StubPermitService(boolean admin) {
            this.admin = admin;
        }

        public Boolean isAdmin() {
            return admin;
        }
    }

    /** 相对今天偏移 N 天的零点 */
    private static Date dayOffset(int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, days);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private static Date dayOffsetAfter(Date day) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(day);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return calendar.getTime();
    }
}
