package com.bidr.llm.model;

import com.bidr.llm.agent.AgentLoopListener;
import com.bidr.llm.agent.AgentLoopOptions;
import com.bidr.llm.agent.AgentLoopResult;
import com.bidr.llm.agent.ToolAgentRunner;
import com.bidr.llm.provider.ModelConfigProvider;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Title: RawSyncChatModelIT
 * Description: 真调**环境验收**——离线用例只能证明请求体带对了字段，"网关是否真照做（关思考）/ 真调工具"
 * 只有真调能证。选型与调用方式见 README「模型层客户端一览」与 7.3，本类只负责断言。
 *
 * <p><b>CI 默认不跑</b>：常规构建的 surefire 只收 {@code *Test}，不含 {@code *IT}；需要时显式调用并给足环境变量：</p>
 * <pre>
 * LLM_IT=1 LLM_IT_BASE_URL=https://your-gateway/compatible-mode/v1 LLM_IT_API_KEY=sk-xxx \
 *     mvn -pl core/llm test -Dtest=RawSyncChatModelIT
 * </pre>
 * <p>三个环境变量缺任一个即整体跳过（{@link Assume}），不污染无凭据环境的构建结果。</p>
 *
 * @author Sharp
 * @since 2026/9/17
 */
public class RawSyncChatModelIT {

    private static final String BASE_URL = System.getenv("LLM_IT_BASE_URL");
    private static final String API_KEY = System.getenv("LLM_IT_API_KEY");
    private static final String MODEL_NAME = envOr("LLM_IT_MODEL", "qwen3.8-flash");

    @Before
    public void requireEnv() {
        Assume.assumeTrue("未设置 LLM_IT=1（真调样例，CI 默认跳过）", "1".equals(System.getenv("LLM_IT")));
        Assume.assumeTrue("未设置 LLM_IT_BASE_URL", BASE_URL != null && !BASE_URL.isEmpty());
        Assume.assumeTrue("未设置 LLM_IT_API_KEY", API_KEY != null && !API_KEY.isEmpty());
    }

    /** 工具样例：注解即 schema 来源（返回值含中文，用于验证据往返不被编码损坏） */
    public static class ScopeTools {

        @Tool("返回当前检索范围（空间与分类维度）。用户问“当前范围/在哪个范围查”时必须调用本工具。")
        public String currentScope() {
            return "SCOPE-OK|空间=IT空间|维度=档案/定期30年";
        }
    }

    /** 环境变量 → 配置提供者：业务侧实际项目换成数据库/配置中心实现即可（接口同签名） */
    private static ModelConfigProvider envProvider() {
        return new ModelConfigProvider() {
            @Override
            public String getBaseUrl(String purposeType) {
                return BASE_URL;
            }

            @Override
            public String getApiKey(String purposeType, Long userId) {
                return API_KEY;
            }

            @Override
            public String getModelName(String purposeType) {
                return MODEL_NAME;
            }

            @Override
            public long getTimeoutSeconds(String purposeType) {
                return 120;
            }

            @Override
            public String getConfigSignatureWithoutKey(String purposeType) {
                return BASE_URL + "|" + MODEL_NAME;
            }
        };
    }

    /**
     * 建模型：{@code extraBody} 按自家网关的扩展字段填（示例是关思考；OpenAI 官方网关通常不需要，
     * 传 null 即可），{@code traceSink} 收审计轨迹，{@code maxAttempts} 管 5xx 重试。
     */
    private static ChatLanguageModel model(Consumer<String> traceSink) {
        Map<String, Object> extraBody = new HashMap<>();
        // 若你的网关支持扩展字段，在这里加；框架只透传不解释。例：extraBody.put("enable_thinking", false);
        return new RawSyncChatModel(envProvider(), "AGENT", null, extraBody, 512, 3, traceSink);
    }

    /** 工具循环端到端：模型决定调工具 → 框架执行 → 结果回填 → 出带事实的结论 */
    @Test
    public void toolLoopThroughRealGateway() {
        List<String> logs = new CopyOnWriteArrayList<>();
        List<String> trace = new CopyOnWriteArrayList<>();
        AgentLoopListener listener = new AgentLoopListener() {
            @Override
            public void log(String line) {
                logs.add(line);
            }

            @Override
            public boolean shouldStop() {
                return false;
            }
        };

        AgentLoopResult result = new ToolAgentRunner().run(model(trace::add),
                "你是知识核查助手。调用工具获取事实，不要凭记忆作答。",
                "请先调用 currentScope 工具获取当前检索范围，然后把范围字符串原样复述给我，不要做任何改写。",
                Collections.singletonList(new ScopeTools()), new AgentLoopOptions(), listener);

        org.junit.Assert.assertFalse("会话不应被停止", result.isStopped());
        org.junit.Assert.assertTrue("至少应有「工具轮 + 结论轮」，实际: " + result.getRounds(), result.getRounds() >= 2);
        org.junit.Assert.assertTrue("工具返回值须进入结论，实际: " + result.getText(),
                result.getText().contains("SCOPE-OK"));
        // 中文证据往返：编码协商坏了的话，检索到的中文证据全会失真
        org.junit.Assert.assertTrue("中文在工具往返中损坏，实际: " + result.getText(),
                result.getText().contains("定期30年"));
        org.junit.Assert.assertTrue("过程日志须含工具调用，实际: " + logs,
                logs.stream().anyMatch(l -> l.contains("调用工具 currentScope")));
        org.junit.Assert.assertFalse("trace 须回调（审计轨迹），实际: " + trace, trace.isEmpty());
    }

    /** 审计轨迹可用性：每行含用量与「思考tokens」，业务可据此核对思考纪律与成本 */
    @Test
    public void traceLineCarriesUsageAndThinkingTokens() {
        List<String> trace = new CopyOnWriteArrayList<>();

        model(trace::add).generate(
                Collections.singletonList(UserMessage.from("请只回答一个数字：2 加 3 等于几？")));

        org.junit.Assert.assertEquals("每次调用一行轨迹", 1, trace.size());
        org.junit.Assert.assertTrue("轨迹须含 token 用量，实际: " + trace.get(0), trace.get(0).contains("tokens="));
        org.junit.Assert.assertTrue("轨迹须含思考 token 数（审计口径），实际: " + trace.get(0),
                trace.get(0).contains("思考tokens="));
    }

    private static String envOr(String key, String defaultValue) {
        String v = System.getenv(key);
        return v == null || v.isEmpty() ? defaultValue : v;
    }
}