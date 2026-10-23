package com.bidr.llm.model;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.output.Response;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Title: StreamingProgressChatModelFinalFrameTest
 * Description: onComplete 最终全文帧回归（M18 P0）：verbose 模式下 1s 节流吞掉的尾部应答
 * 必须由 onComplete 补推「应答中·已收 N 字\n+完整累积全文」兜底（ask_user 提问卡弹出时
 * live 不停在半句），且该帧在思考归档帧之前（最终应答先进 live，归档走事件流）；
 * 非 verbose 分支不补全文帧（仅状态行/归档，追加式事件流兼容保留）。
 * 测试基座＝子类化 RawSseStreamingChatModel（构造器纯赋值、传 null 安全）劫持 generate
 * 为同步回调，无需起 HTTP 桩。
 *
 * @author Sharp
 * @since 2026/10/23
 */
public class StreamingProgressChatModelFinalFrameTest {

    /** 桩 delegate：generate 劫持为按脚本同步回调（call() 的闩在脚本内 onComplete 解开，主线程不阻塞） */
    private static class StubSseModel extends RawSseStreamingChatModel {
        private final Consumer<Listener> script;

        StubSseModel(Consumer<Listener> script) {
            super(null, "test", null);
            this.script = script;
        }

        @Override
        public void generate(List<ChatMessage> messages, List<ToolSpecification> toolSpecs, Listener listener) {
            script.accept(listener);
        }
    }

    /** 同一事件脚本（思考 2 段 + 应答 3 段，后 2 段 token 落在 1s 节流窗内被吞）跑一轮，收齐 sink 全部帧 */
    private static List<String> run(boolean verbose, List<String> reasoningDeltas, List<String> tokenDeltas) {
        List<String> frames = new ArrayList<>();
        StubSseModel delegate = new StubSseModel(listener -> {
            for (String d : reasoningDeltas) {
                listener.onReasoning(d);
            }
            for (String d : tokenDeltas) {
                listener.onToken(d);
            }
            listener.onComplete(Response.from(AiMessage.from(String.join("", tokenDeltas))));
        });
        StreamingProgressChatModel model = new StreamingProgressChatModel(delegate, frames::add, 30, null, verbose);
        Response<AiMessage> resp = model.generate(Collections.singletonList(UserMessage.from("hi")));
        Assert.assertNotNull("onComplete 响应应透传返回", resp.content());
        return frames;
    }

    private static String lastFrameWithPrefix(List<String> frames, String prefix) {
        String last = null;
        for (String f : frames) {
            if (f.startsWith(prefix)) {
                last = f;
            }
        }
        return last;
    }

    private static int firstIndexOfPrefix(List<String> frames, String prefix) {
        for (int i = 0; i < frames.size(); i++) {
            if (frames.get(i).startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    /** verbose 腿：节流吞尾后，onComplete 补推的最后一条「应答中」帧＝状态行（字数＝全部 delta 之和）+完整全文 */
    @Test
    public void verboseOnCompletePushesFinalFullAnswerFrame() {
        List<String> reasoningDeltas = Arrays.asList("先想", "一段");
        List<String> tokenDeltas = Arrays.asList("答", "案第二段", "，尾巴没到就是这帧补的");
        String full = String.join("", tokenDeltas);
        List<String> frames = run(true, reasoningDeltas, tokenDeltas);

        String expectedFinal = "应答中·已收 " + full.length() + " 字\n" + full;
        String lastAnswer = lastFrameWithPrefix(frames, "应答中");
        Assert.assertNotNull("verbose 模式应有「应答中」帧", lastAnswer);
        Assert.assertEquals("最后一条应答帧须为 onComplete 补推的完整全文（字数精确＝全部 delta 之和）",
                expectedFinal, lastAnswer);

        // 顺序语义：最终全文帧在思考归档帧之前（最终应答先进 live，归档走事件流）
        int finalIdx = frames.indexOf(expectedFinal);
        int archiveIdx = firstIndexOfPrefix(frames, "【LLM 思考归档】");
        Assert.assertTrue("思考归档帧应存在", archiveIdx >= 0);
        Assert.assertTrue("最终全文帧应在归档帧之前（final=" + finalIdx + ", archive=" + archiveIdx + "）",
                finalIdx >= 0 && finalIdx < archiveIdx);

        // 节流确实吞过帧：流中首 token 帧（残文）与最终帧不同，证明兜底帧补的不是重复内容
        long answerFrames = frames.stream().filter(f -> f.startsWith("应答中")).count();
        Assert.assertTrue("流中应答帧应≥2（首 token 立推 + onComplete 兜底），实得 " + answerFrames,
                answerFrames >= 2);
    }

    /** 纯思考流腿（护栏回归）：onReasoning 有、onToken 零——onComplete 后没有
     *  「应答中·已收 0 字」空帧（contentChars>0 护栏），思考归档帧照常兜底 */
    @Test
    public void 纯思考流onComplete后无已收0字空帧() {
        List<String> frames = run(true, Arrays.asList("只想", "不答"), Collections.emptyList());

        Assert.assertTrue("纯思考流不应有任何「应答中」帧（含 onComplete 的「已收 0 字」空帧）："
                        + frames, frames.stream().noneMatch(f -> f.startsWith("应答中")));
        Assert.assertTrue("思考归档帧照常兜底（reasoningChars>0）",
                firstIndexOfPrefix(frames, "【LLM 思考归档】") >= 0);
    }

    /** 纯应答流腿（护栏回归，R2 S-5）：onReasoning 零、onToken 有——思考→应答切换点
     *  不推「思考中·已思 0 字」空帧（reasoningChars>0 护栏，对齐其注释「纯应答调用
     *  天然跳过」），最终全文帧照常由 onComplete 兜底 */
    @Test
    public void 纯应答流无已思0字空帧且最终帧照常() {
        List<String> tokenDeltas = Arrays.asList("答一", "答二尾巴");
        List<String> frames = run(true, Collections.emptyList(), tokenDeltas);
        String full = String.join("", tokenDeltas);

        Assert.assertTrue("纯应答流不应有任何「思考中」帧（含切换点的「已思 0 字」空帧）："
                        + frames, frames.stream().noneMatch(f -> f.startsWith("思考中")));
        Assert.assertEquals("纯应答流最终全文帧照常兜底（字数精确＝全部 delta 之和）",
                "应答中·已收 " + full.length() + " 字\n" + full,
                lastFrameWithPrefix(frames, "应答中"));
    }

    /** 非 verbose 腿：onComplete 后没有「应答中·已收 N 字\n+全文」形态的帧（仅状态行/归档，兼容保留） */
    @Test
    public void nonVerboseOnCompleteKeepsStatusLineOnly() {
        List<String> reasoningDeltas = Arrays.asList("先想", "一段");
        List<String> tokenDeltas = Arrays.asList("答", "案第二段", "，尾巴没到就是这帧补的");
        String full = String.join("", tokenDeltas);
        List<String> frames = run(false, reasoningDeltas, tokenDeltas);

        for (String f : frames) {
            if (f.startsWith("应答中")) {
                Assert.assertFalse("非 verbose 应答帧只允许状态行（不携带全文）：" + f, f.contains("\n"));
            }
        }
        Assert.assertFalse("非 verbose onComplete 不得补推最终全文帧",
                frames.contains("应答中·已收 " + full.length() + " 字\n" + full));
        Assert.assertTrue("思考归档仍由 onComplete 兜底（与本棒无关，顺带守住不回归）",
                firstIndexOfPrefix(frames, "【LLM 思考归档】") >= 0);
    }
}
