package com.bidr.llm.agent.session;

import com.bidr.kernel.constant.err.ErrCodeLevel;
import com.bidr.kernel.constant.err.ErrCodeSys;
import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.agent.AgentSessionController;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: AgentSessionUserRejectionLevelTest
 * Description: agent 会话控制面的告警分级钉桩——把"客户端动作造成的拒绝"与"服务端故障"分档：
 * <ul>
 *     <li>用户态拒绝（会话过期、终态后再点暂停/作答、问题条目已被处理、入参缺失/非法、标识为空）
 *         必须抛 {@link ServiceException}：errCode＝SYS_ERR_MSG＝<b>WARN 级</b>。
 *         告警邮件监听的放行条件是"事件级别 ≥ 配置最低级别（缺省 ERROR）"，WARN 够不上＝<b>不发邮件</b>；
 *         {@code ErrCodeLevel.log} 也只出单行 WARN，不再刷 ERROR 全文栈；</li>
 *     <li>服务端故障（未注册 agentKey、启动期重复注册、作答序列化失败）仍抛 IllegalState/IllegalArgument，
 *         落全局兜底口＝SYS_ERR/ERROR＝<b>保留邮件告警</b>。</li>
 * </ul>
 * 前端文案逐字不变（ServiceException 单参构造只套 %s，不经字典改写），差别仅在响应从 HTTP 500
 * 变成错误信封 code 101。
 *
 * @author sharp
 * @since 2026-10-19
 */
public class AgentSessionUserRejectionLevelTest {

    private static final String GONE = "no-such-session";
    private static final String LIVE = "live-1";

    /** 共享空库：过期腿直接打不存在的 id，在库腿经 save() 播种后走真实读回路径 */
    private final InMemoryAgentSessionStore store = new InMemoryAgentSessionStore();

    private AgentSessionService service() {
        return new AgentSessionService(store, Collections.emptyList(), Mockito.mock(ObjectProvider.class));
    }

    /** 端点依赖除 operatorResolver 外全喂 null：下列腿都在触达它们之前抛出 */
    private AgentSessionController controller() {
        return new AgentSessionController(null, null, null, Collections.emptyList(),
                Mockito.mock(ObjectProvider.class), null,
                Mockito.mock(ObjectProvider.class), Collections.emptyList());
    }

    private void save(String status, List<AgentQuestion> questions, List<AgentConfirmation> confirmations) {
        AgentSessionState state = new AgentSessionState();
        state.setSessionId(LIVE);
        state.setStatus(status);
        state.setQuestions(new ArrayList<>(questions));
        state.setConfirmations(new ArrayList<>(confirmations));
        store.saveState(state);
    }

    private static AgentQuestion question(int id, String status) {
        AgentQuestion q = new AgentQuestion();
        q.setId(id);
        q.setStatus(status);
        return q;
    }

    private static AgentConfirmation confirmation(int id, String status) {
        AgentConfirmation c = new AgentConfirmation();
        c.setId(id);
        c.setStatus(status);
        return c;
    }

    private static void assertWarnLeg(String expectFragment, Runnable call) {
        try {
            call.run();
            Assert.fail("应抛 ServiceException(WARN) 的人话拒绝，实际正常返回");
        } catch (ServiceException e) {
            Assert.assertEquals("错误码应落在 SYS_ERR_MSG（WARN 档＝邮件告警射程之外）",
                    ErrCodeSys.SYS_ERR_MSG, e.getErrCode());
            Assert.assertEquals(ErrCodeLevel.WARN, e.getErrCode().getErrLevel());
            Assert.assertTrue("文案须逐字保留，实际=" + e.getMessage(), e.getMessage().contains(expectFragment));
        } catch (IllegalArgumentException | IllegalStateException e) {
            Assert.fail("不得再落全局兜底口（→SYS_ERR/ERROR＝发邮件＋全文栈）: " + e);
        }
    }

    // ==================== 会话过期：读腿与控制腿（前端 2s 轮询与点赞的正常归宿） ====================

    @Test
    public void expiredSessionReadAndControlLegsAreWarnLevel() {
        assertWarnLeg("会话不存在或已过期", () -> service().status(GONE));
        assertWarnLeg("会话不存在或已过期", () -> service().events(GONE, 0));
        assertWarnLeg("会话不存在或已过期", () -> service().pause(GONE, null));
        assertWarnLeg("会话不存在或已过期", () -> service().resume(GONE, null));
        assertWarnLeg("会话不存在或已过期", () -> service().answer(GONE, 1, "答", false));
        assertWarnLeg("会话不存在或已过期", () -> service().stop(GONE));
        assertWarnLeg("会话不存在或已过期", () -> service().resolveConfirmation(GONE, 1, false, null));
    }

    // ==================== 终态后再点暂停/恢复/作答 ====================

    @Test
    public void terminalSessionActionRejectionsAreWarnLevel() {
        save(AgentSessionState.FINISHED, Collections.emptyList(), Collections.emptyList());
        assertWarnLeg("会话已结束", () -> service().pause(LIVE, null));
        assertWarnLeg("会话已结束", () -> service().resume(LIVE, null));
        assertWarnLeg("会话已结束", () -> service().answer(LIVE, 1, "答", false));
    }

    // ==================== 问题/确认条目的时序拒绝（本次用户报的第二条） ====================

    @Test
    public void staleOrAlreadyHandledQuestionLegsAreWarnLevel() {
        save(AgentSessionState.RUNNING, Collections.singletonList(question(1, AgentQuestion.SKIPPED)),
                Collections.singletonList(confirmation(7, AgentConfirmation.PENDING)));
        assertWarnLeg("问题不存在", () -> service().answer(LIVE, 999, "答", false));
        // B-04（2026-10-22）：同一张提问卡双击/两个页签各答一次/超时后再点跳过——迟到作答＝无效操作
        // 不是服务异常，降为幂等 WARN 直接返回（问题状态不变、不重复提交）；
        // 无效性由前端问题卡置灰兜住入口（answerQuestion catch＋事件置灰）
        service().answer(LIVE, 1, "答", false);
        assertWarnLeg("待确认条目不存在", () -> service().resolveConfirmation(LIVE, 404, false, null));
        assertWarnLeg("改口径须给出新口径说明", () -> service().resolveConfirmation(LIVE, 7, true, "  "));
    }

    // ==================== 端点入参校验同族 ====================

    @Test
    public void badRequestParamsAreWarnLevel() {
        AgentSessionController.SessionStartReq blankKey = new AgentSessionController.SessionStartReq();
        blankKey.setAgentKey("  ");
        assertWarnLeg("agentKey 不能为空", () -> controller().start(blankKey));

        AgentSessionController.SessionStartReq badPolicy = new AgentSessionController.SessionStartReq();
        badPolicy.setAgentKey("any-agent");
        badPolicy.setDetachPolicy("NOT_A_POLICY");
        assertWarnLeg("detachPolicy 取值非法", () -> controller().start(badPolicy));

        assertWarnLeg("questionId 不能为空",
                () -> controller().answer(LIVE, new AgentSessionController.AnswerReq()));

        AgentSessionController.AnswerReq emptyAnswer = new AgentSessionController.AnswerReq();
        emptyAnswer.setQuestionId(1);
        emptyAnswer.setAnswer("   ");
        assertWarnLeg("作答内容与跳过至少其一", () -> controller().answer(LIVE, emptyAnswer));

        AgentSessionController.RatingSaveReq badRating = new AgentSessionController.RatingSaveReq();
        badRating.setSkillCode("chatbi");
        badRating.setRatingId("c:m");
        badRating.setRating("meh");
        assertWarnLeg("评价取值非法", () -> controller().saveRating(badRating));

        assertWarnLeg("轨迹标识不能为空", () -> controller().getFlowTrace(""));
        assertWarnLeg("skill 标识不能为空",
                () -> controller().ratingStat("", null, null, null, null, null, null));
    }

    // ==================== 边界对照：真故障那一档仍留在告警射程 ====================

    @Test
    public void genuineServerFaultKeepsErrorLevelAlerting() {
        try {
            service().start("not-registered", Collections.emptyMap(), "tester");
            Assert.fail("未注册 agentKey＝装配问题，应仍抛 IllegalArgumentException 进 ERROR 告警");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("未注册的 agent"));
        }
    }

    @Test
    public void mailAlertThresholdSitsAtErrorSoWarnIsExcluded() {
        // 分级判据自证：兜底口用的 SYS_ERR 是 ERROR（够上缺省门槛→发邮件），本次改用的 SYS_ERR_MSG 是 WARN（不够）
        Assert.assertEquals(ErrCodeLevel.ERROR, ErrCodeSys.SYS_ERR.getErrLevel());
        Assert.assertEquals(ErrCodeLevel.WARN, ErrCodeSys.SYS_ERR_MSG.getErrLevel());
        Assert.assertTrue(Integer.parseInt(ErrCodeSys.SYS_ERR.getErrLevel().getValue())
                >= Integer.parseInt(ErrCodeLevel.ERROR.getValue()));
        Assert.assertTrue(Integer.parseInt(ErrCodeSys.SYS_ERR_MSG.getErrLevel().getValue())
                < Integer.parseInt(ErrCodeLevel.ERROR.getValue()));
    }
}
