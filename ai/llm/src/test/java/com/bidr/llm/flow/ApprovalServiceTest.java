package com.bidr.llm.flow;

import com.bidr.kernel.exception.ServiceException;
import com.bidr.llm.flow.approval.ApprovalRecord;
import com.bidr.llm.flow.approval.ApprovalRecordStore;
import com.bidr.llm.flow.approval.ApprovalService;
import com.bidr.llm.flow.approval.InMemoryApprovalRecordStore;
import com.bidr.llm.flow.trace.FlowTraceRecorder;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Title: ApprovalServiceTest
 * Description: 审批持久化驱动 resume 用例（T4.1 门面）：approve → FlowEngine.resume(nodeId, context)
 * 被调用且上下文最小构造（graph=当前生效编排、决定/意见注入变量池、发起人回填）/
 * reject 不续跑 / 重复决定与未知记录与非法决定的防线 / InMemory 存储的队列 FIFO 与历史排序截断。
 * FlowEngine 用测试子类捕获 resume（不起容器，轨迹记录器降级形态）。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class ApprovalServiceTest {

    private static final String FLOW_KEY = "test-flow";

    /**
     * 可控生效编排：start → llm挂起结点(n2) → output，结构合法即可（本测试不真跑执行器）
     */
    private static FlowGraph activeGraph() {
        FlowGraph graph = new FlowGraph();
        FlowGraph.FlowNode start = new FlowGraph.FlowNode();
        start.setId("s1");
        start.setType("start");
        FlowGraph.FlowNode pending = new FlowGraph.FlowNode();
        pending.setId("n2");
        pending.setType("llm");
        FlowGraph.FlowNode output = new FlowGraph.FlowNode();
        output.setId("o1");
        output.setType("output");
        FlowGraph.FlowEdge e1 = new FlowGraph.FlowEdge();
        e1.setSource("s1");
        e1.setTarget("n2");
        FlowGraph.FlowEdge e2 = new FlowGraph.FlowEdge();
        e2.setSource("n2");
        e2.setTarget("o1");
        graph.setNodes(new ArrayList<>(java.util.Arrays.asList(start, pending, output)));
        graph.setEdges(new ArrayList<>(java.util.Arrays.asList(e1, e2)));
        return graph;
    }

    /**
     * 测试引擎：loadFlow 返回固定生效编排，resume 捕获调用（nodeId + 上下文）
     */
    private static FlowEngine resumeCapturingEngine(AtomicReference<String> nodeIdRef,
                                                    AtomicReference<FlowContext> ctxRef) {
        return new FlowEngine(null, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>()) {
            @Override
            public LoadedFlow loadFlow(String flowKey) {
                return new LoadedFlow(activeGraph(), false, flowKey);
            }

            @Override
            public void resume(String nodeId, FlowContext context) {
                nodeIdRef.set(nodeId);
                ctxRef.set(context);
            }
        };
    }

    private static ApprovalService service(ApprovalRecordStore store, FlowEngine engine) {
        return new ApprovalService(new StaticObjectProvider<>(store), engine, degradedRecorder());
    }

    private static FlowTraceRecorder degradedRecorder() {
        return new FlowTraceRecorder(new StaticObjectProvider<>(null), new StaticObjectProvider<>(null));
    }

    /** approve 主链路：决定落库（审批人/时间回填）→ resume(nodeId, context) 被调用，上下文携带生效编排与审批变量 */
    @Test
    public void approveDrivesResume() {
        InMemoryApprovalRecordStore store = new InMemoryApprovalRecordStore();
        AtomicReference<String> nodeIdRef = new AtomicReference<>();
        AtomicReference<FlowContext> ctxRef = new AtomicReference<>();
        ApprovalService service = service(store, resumeCapturingEngine(nodeIdRef, ctxRef));

        ApprovalRecord submitted = service.submit(FLOW_KEY, "trace-1", "n2", "发起人甲", "高危操作请把关");
        Assert.assertNotNull("recordId 由存储方生成", submitted.getRecordId());
        Assert.assertNull("新记录待审", submitted.getDecision());
        Assert.assertEquals(1, service.listPending(FLOW_KEY).size());

        ApprovalRecord decided = service.approveRecord(submitted.getRecordId(),
                ApprovalService.DECISION_APPROVE, "审批人乙", "同意执行");

        Assert.assertEquals("决定=APPROVE", ApprovalService.DECISION_APPROVE, decided.getDecision());
        Assert.assertEquals("审批人回填", "审批人乙", decided.getDecidedBy());
        Assert.assertEquals("审批意见与提交说明分开", "同意执行", decided.getDecisionComment());
        Assert.assertEquals("提交说明保留", "高危操作请把关", decided.getComment());
        Assert.assertNotNull("决定时间回填", decided.getDecidedAt());
        Assert.assertTrue("队列已清空", service.listPending(FLOW_KEY).isEmpty());
        Assert.assertEquals(1, service.history(FLOW_KEY, 10).size());

        Assert.assertEquals("resume 从挂起结点续跑", "n2", nodeIdRef.get());
        FlowContext ctx = ctxRef.get();
        Assert.assertNotNull("续跑上下文已构造", ctx);
        Assert.assertNotNull("graph=当前生效编排", ctx.getGraph());
        Assert.assertEquals("决定注入变量池", ApprovalService.DECISION_APPROVE,
                ctx.getVariable(ApprovalService.VAR_APPROVAL_DECISION));
        Assert.assertEquals("意见注入变量池", "同意执行",
                ctx.getVariable(ApprovalService.VAR_APPROVAL_COMMENT));
        Assert.assertEquals("访问人回填审批人", "审批人乙", ctx.getOperator());
    }

    /** reject 不续跑：决定落库进历史，resume 从未被调用 */
    @Test
    public void rejectDoesNotResume() {
        InMemoryApprovalRecordStore store = new InMemoryApprovalRecordStore();
        AtomicReference<String> nodeIdRef = new AtomicReference<>();
        ApprovalService service = service(store, resumeCapturingEngine(nodeIdRef, new AtomicReference<>()));

        ApprovalRecord submitted = service.submit(FLOW_KEY, "trace-2", "n2", null, null);
        ApprovalRecord decided = service.approveRecord(submitted.getRecordId(),
                ApprovalService.DECISION_REJECT, "审批人乙", "驳回不续跑");

        Assert.assertEquals(ApprovalService.DECISION_REJECT, decided.getDecision());
        Assert.assertNull("驳回不触发续跑", nodeIdRef.get());
        Assert.assertTrue(service.listPending(FLOW_KEY).isEmpty());
    }

    /** 防线：未知记录 / 重复决定 / 非法决定取值 / 提交缺参 / 无存储装配 */
    @Test
    public void guards() {
        InMemoryApprovalRecordStore store = new InMemoryApprovalRecordStore();
        ApprovalService service = service(store, resumeCapturingEngine(new AtomicReference<>(), new AtomicReference<>()));

        try {
            service.approveRecord("ghost", ApprovalService.DECISION_APPROVE, null, null);
            Assert.fail("未知记录应报错");
        } catch (ServiceException e) {
            Assert.assertTrue("提示记录不存在: " + e.getMessage(), e.getMessage().contains("不存在"));
        }

        ApprovalRecord record = service.submit(FLOW_KEY, null, "n2", null, null);
        service.approveRecord(record.getRecordId(), ApprovalService.DECISION_APPROVE, null, null);
        try {
            service.approveRecord(record.getRecordId(), ApprovalService.DECISION_APPROVE, null, null);
            Assert.fail("重复决定应报错");
        } catch (ServiceException e) {
            Assert.assertTrue("提示已决定: " + e.getMessage(), e.getMessage().contains("已决定"));
        }
        // 决定人缺省回落"审批台"
        Assert.assertEquals("审批台", store.find(record.getRecordId()).getDecidedBy());

        try {
            service.approveRecord("ghost", "MAYBE", null, null);
            Assert.fail("非法决定取值应报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("APPROVE"));
        }
        try {
            service.submit(null, null, "n2", null, null);
            Assert.fail("缺 flowKey 应报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("流程标识"));
        }
        try {
            service.submit(FLOW_KEY, null, " ", null, null);
            Assert.fail("缺续跑结点应报错");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("结点"));
        }

        // 未接入审批存储：明确报错不静默
        ApprovalService noStore = new ApprovalService(new StaticObjectProvider<>(null),
                resumeCapturingEngine(new AtomicReference<>(), new AtomicReference<>()), degradedRecorder());
        try {
            noStore.listPending(FLOW_KEY);
            Assert.fail("无存储应报未接入");
        } catch (ServiceException e) {
            Assert.assertTrue(e.getMessage().contains("未接入"));
        }
    }

    /** InMemory 存储：待审 FIFO（旧→新）、历史新→旧且按 limit 截断、find 精确命中 */
    @Test
    public void inMemoryStoreQueueAndHistory() {
        InMemoryApprovalRecordStore store = new InMemoryApprovalRecordStore();
        ApprovalService service = service(store, resumeCapturingEngine(new AtomicReference<>(), new AtomicReference<>()));

        ApprovalRecord first = service.submit(FLOW_KEY, "r1", "n2", null, null);
        ApprovalRecord second = service.submit(FLOW_KEY, "r2", "n2", null, null);
        // 让时间戳可分（同毫秒提交也能靠 id 保序，这里直接显式错开）
        first.setCreatedAt(1000L);
        second.setCreatedAt(2000L);
        store.save(first);
        store.save(second);

        List<ApprovalRecord> pending = service.listPending(FLOW_KEY);
        Assert.assertEquals("待审 FIFO：旧→新", first.getRecordId(), pending.get(0).getRecordId());

        service.approveRecord(first.getRecordId(), ApprovalService.DECISION_REJECT, null, null);
        List<ApprovalRecord> history = service.history(FLOW_KEY, 10);
        Assert.assertEquals(1, history.size());
        Assert.assertEquals(first.getRecordId(), history.get(0).getRecordId());

        service.approveRecord(second.getRecordId(), ApprovalService.DECISION_APPROVE, null, null);
        // second.createdAt 更新决定后仍是提交时间口径？——决定不改 createdAt，历史仍按 createdAt 排序
        Assert.assertEquals(2, service.history(FLOW_KEY, 10).size());
        Assert.assertEquals("历史新→旧（second 提交更晚）", second.getRecordId(),
                service.history(FLOW_KEY, 10).get(0).getRecordId());
        Assert.assertEquals("limit 截断", 1, service.history(FLOW_KEY, 1).size());
        Assert.assertEquals("find 精确命中", first.getRecordId(), store.find(first.getRecordId()).getRecordId());
        Assert.assertNull("find 未命中返 null", store.find("ghost"));
    }
}
