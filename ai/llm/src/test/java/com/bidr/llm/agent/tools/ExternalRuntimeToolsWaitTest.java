package com.bidr.llm.agent.tools;

import com.bidr.llm.agent.external.ExternalRuntimeTaskService;
import com.bidr.llm.agent.session.InMemoryAgentSessionStore;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: ExternalRuntimeToolsWaitTest
 * Description: {@code waitExternalTask} 有界阻塞等待的六态契约（离线，脚本化进度桩）：
 * ① 终局即返不空等；② RUNNING 多轮阻塞到终局；③ 超时返瘦身快照不挂死；
 * ④ 失败/停止透因且不被当成"已读完"；⑤ 未知句柄友好语而非异常栈；⑥ 可中断（恢复中断位）。
 *
 * @author sharp
 * @since 2026/9/24
 */
public class ExternalRuntimeToolsWaitTest {

    /** 脚本化服务：按序吐 progress 快照（末帧驻留），记录调用次数；throwUnknown=模拟未知句柄 */
    private static final class ScriptedService extends ExternalRuntimeTaskService {
        private final List<Map<String, Object>> frames;
        private final boolean throwUnknown;
        private int index;
        private int progressCalls;

        ScriptedService(List<Map<String, Object>> frames) {
            this(frames, false);
        }

        ScriptedService(List<Map<String, Object>> frames, boolean throwUnknown) {
            super(new InMemoryAgentSessionStore(), () -> null);
            this.frames = frames;
            this.throwUnknown = throwUnknown;
        }

        @Override
        public Map<String, Object> progress(String taskId, long cursorIn, int maxEvents) {
            progressCalls++;
            if (throwUnknown) {
                throw new IllegalArgumentException("外部任务不存在或已过期：" + taskId);
            }
            Map<String, Object> frame = frames.get(Math.min(index, frames.size() - 1));
            if (index < frames.size() - 1) {
                index++;
            }
            return frame;
        }
    }

    private static Map<String, Object> frame(String status, String live, String error, String summary) {
        Map<String, Object> m = new HashMap<>();
        m.put("taskId", "t1");
        m.put("status", status);
        if (live != null) {
            m.put("live", live);
        }
        if (error != null) {
            m.put("error", error);
        }
        if (summary != null) {
            m.put("summary", summary);
        }
        return m;
    }

    @Test
    public void 终局即返不空等() {
        ScriptedService service = new ScriptedService(Arrays.asList(
                frame("FINISHED", null, null, "答案是 3")));
        ExternalRuntimeTools tools = new ExternalRuntimeTools(service, "parent", 10L, 200L);

        String out = tools.waitExternalTask("t1");

        Assert.assertTrue("FINISHED 须指向取结论且提醒只取一次",
                out.contains("任务 FINISHED") && out.contains("readExternalTaskResult(\"t1\")")
                        && out.contains("取过就不要重复取"));
        Assert.assertEquals(1, service.progressCalls);
    }

    @Test
    public void RUNNING多轮阻塞到终局() {
        ScriptedService service = new ScriptedService(Arrays.asList(
                frame("RUNNING", "思考中", null, null),
                frame("RUNNING", "写脚本中", null, null),
                frame("FINISHED", null, null, "结论")));
        ExternalRuntimeTools tools = new ExternalRuntimeTools(service, "parent", 5L, 500L);

        String out = tools.waitExternalTask("t1");

        Assert.assertTrue(out.contains("任务 FINISHED"));
        Assert.assertEquals("RUNNING 期间必须继续阻塞探询而不是直接回模型", 3, service.progressCalls);
    }

    @Test
    public void 超时返瘦身快照不挂死() {
        ScriptedService service = new ScriptedService(Arrays.asList(
                frame("RUNNING", "思考中 · 已思 4210 字", null, null)));
        ExternalRuntimeTools tools = new ExternalRuntimeTools(service, "parent", 5L, 60L);

        String out = tools.waitExternalTask("t1");

        Assert.assertTrue("超时返回必须带上限秒数与 live 一行",
                out.contains("仍 RUNNING") && out.contains("s 后超时返回")
                        && out.contains("已思 4210 字") && out.contains("再次调用 waitExternalTask"));
    }

    @Test
    public void 失败透因不被当成已读完() {
        ScriptedService service = new ScriptedService(Arrays.asList(
                frame("FAILED", null, "Agent reached maximum iterations limit", "已下载文件但统计未跑完")));
        ExternalRuntimeTools tools = new ExternalRuntimeTools(service, "parent", 10L, 200L);

        String out = tools.waitExternalTask("t1");

        Assert.assertTrue("失败须带原因截文与部分产出定性，并禁止当作已完成",
                out.contains("任务 FAILED") && out.contains("未正常完成，不得当作已完成")
                        && out.contains("原因=Agent reached maximum iterations limit")
                        && out.contains("部分产出") && out.contains("已下载文件但统计未跑完"));
    }

    @Test
    public void 未知句柄给友好语不抛栈() {
        ScriptedService service = new ScriptedService(
                Arrays.asList(frame("RUNNING", null, null, null)), true);
        ExternalRuntimeTools tools = new ExternalRuntimeTools(service, "parent", 10L, 200L);

        String out = tools.waitExternalTask("gone");

        Assert.assertTrue(out.contains("外部任务不存在或已过期") && out.contains("重新派发"));
    }

    @Test
    public void 可中断且恢复中断位() throws Exception {
        ScriptedService service = new ScriptedService(Arrays.asList(
                frame("RUNNING", "长任务", null, null)));
        ExternalRuntimeTools tools = new ExternalRuntimeTools(service, "parent", 5L, 60_000L);

        final List<String> result = new ArrayList<>();
        final List<Boolean> interrupted = new ArrayList<>();
        Thread worker = new Thread(() -> {
            result.add(tools.waitExternalTask("t1"));
            interrupted.add(Thread.currentThread().isInterrupted());
        });
        worker.start();
        Thread.sleep(50L);
        worker.interrupt();
        worker.join(2000L);

        Assert.assertFalse("等待必须被中断解除而不是跑满 60s", worker.isAlive());
        Assert.assertEquals("等待被中断（会话可能正在停止）。", result.get(0));
        Assert.assertTrue("中断位必须恢复（框架停止语义依赖）", interrupted.get(0));
        Assert.assertTrue("中断前至少探询过一次进度", service.progressCalls >= 1);
    }
}
