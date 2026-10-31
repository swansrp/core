package com.bidr.llm.memory;

import com.bidr.llm.flow.StaticObjectProvider;
import org.junit.Assert;
import org.junit.Test;

/**
 * Title: MemoryInjectionTest
 * Description: 记忆上下文注入用例（T4.3 记忆库）：同一用户第二次会话可见第一次会话写入的
 * fact（两次 inject 断言第二次含第一次内容）；无记忆原文返回；他人记忆不串号；null prompt 可生成注入段。
 *
 * @author Sharp
 * @since 2026/11/1
 */
public class MemoryInjectionTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 100L;
    private static final String BASE_PROMPT = "你是 InKHub 助手，回答保持简洁。";

    private MemoryService memoryService;
    private MemoryContextInjector injector;

    private MemoryContextInjector serviceAndInjector() {
        memoryService = new MemoryService(
                new StaticObjectProvider<>(new InMemoryMemoryStore(1000)));
        injector = new MemoryContextInjector(memoryService, MemoryContextInjector.DEFAULT_TOP_N);
        return injector;
    }

    /**
     * 同一用户第二次会话可见第一次会话写入的 fact：
     * 第一次 inject（无记忆）原文返回；写入 fact 后第二次 inject 含「已知用户上下文：」+ 该内容
     */
    @Test
    public void secondSessionSeesFirstSessionFact() {
        MemoryContextInjector localInjector = serviceAndInjector();

        // 第一次会话：记忆库为空，systemPrompt 原样返回（不注入空段）
        String first = localInjector.injectInto(TENANT, USER, BASE_PROMPT);
        Assert.assertEquals(BASE_PROMPT, first);
        Assert.assertFalse(first.contains(MemoryContextInjector.CONTEXT_HEADER));

        // 第一次会话过程中写入一条 fact
        memoryService.write(TENANT, USER, MemoryKind.FACT, "用户的项目代号叫 InkHub");

        // 第二次会话：注入段出现，fact 内容可见，原提示词仍在最前
        String second = localInjector.injectInto(TENANT, USER, BASE_PROMPT);
        Assert.assertTrue("第二次注入应含标题段", second.contains(MemoryContextInjector.CONTEXT_HEADER));
        Assert.assertTrue("第二次注入应含第一次会话的 fact",
                second.contains("用户的项目代号叫 InkHub"));
        Assert.assertTrue("原始 systemPrompt 应保留在注入段之前",
                second.startsWith(BASE_PROMPT));
        Assert.assertTrue(second.indexOf(BASE_PROMPT) < second.indexOf(MemoryContextInjector.CONTEXT_HEADER));
    }

    /**
     * 记忆按租户+用户隔离：他人的记忆不串进当前会话
     */
    @Test
    public void injectionIsolatedByTenantAndUser() {
        MemoryContextInjector localInjector = serviceAndInjector();
        memoryService.write(9L, 999L, MemoryKind.FACT, "别人家的秘密");
        memoryService.write(TENANT, 200L, MemoryKind.FACT, "隔壁用户的事实");

        String injected = localInjector.injectInto(TENANT, USER, BASE_PROMPT);
        Assert.assertEquals("他人记忆不可见", BASE_PROMPT, injected);

        memoryService.write(TENANT, USER, MemoryKind.PREFERENCE, "本人偏好中文回答");
        injected = localInjector.injectInto(TENANT, USER, BASE_PROMPT);
        Assert.assertTrue(injected.contains("本人偏好中文回答"));
        Assert.assertFalse(injected.contains("别人家的秘密"));
        Assert.assertFalse(injected.contains("隔壁用户的事实"));
    }

    /**
     * 多条记忆逐行格式化「- [类别] 正文」；priority 高的排前；null prompt 也能生成注入段
     */
    @Test
    public void multiRecordFormatAndNullPrompt() {
        MemoryContextInjector localInjector = serviceAndInjector();
        memoryService.write(TENANT, USER, MemoryKind.FACT, "项目在杭州", 0, 30);
        memoryService.write(TENANT, USER, MemoryKind.PREFERENCE, "偏好深色主题", 3, 30);

        String injected = localInjector.injectInto(TENANT, USER, null);
        Assert.assertTrue(injected.startsWith(MemoryContextInjector.CONTEXT_HEADER));
        Assert.assertTrue(injected.contains("- [PREFERENCE] 偏好深色主题"));
        Assert.assertTrue(injected.contains("- [FACT] 项目在杭州"));
        Assert.assertTrue("高优先级应排在前面",
                injected.indexOf("偏好深色主题") < injected.indexOf("项目在杭州"));
    }

    /**
     * 过期记忆不注入：recall 过滤后为空 → 原文返回（store 引用显式持有，回拨 expireAt 后覆盖保存）
     */
    @Test
    public void expiredMemoryNotInjected() {
        InMemoryMemoryStore store = new InMemoryMemoryStore(1000);
        memoryService = new MemoryService(new StaticObjectProvider<>(store));
        injector = new MemoryContextInjector(memoryService, MemoryContextInjector.DEFAULT_TOP_N);

        MemoryRecord stale = memoryService.write(TENANT, USER, MemoryKind.FACT, "过期的事实", 0, 30);
        stale.setExpireAt(System.currentTimeMillis() - 1000L);
        store.save(stale);

        String injected = injector.injectInto(TENANT, USER, BASE_PROMPT);
        Assert.assertEquals("过期记忆不应注入", BASE_PROMPT, injected);
    }

    /**
     * topN 注入上限：召回按 priority 截断，注入段只含 top-N 条
     */
    @Test
    public void injectRespectsTopNLimit() {
        MemoryContextInjector localInjector = serviceAndInjector();
        for (int i = 1; i <= 15; i++) {
            memoryService.write(TENANT, USER, MemoryKind.FACT, "记忆条目" + i, i, 30);
        }
        String injected = localInjector.injectInto(TENANT, USER, BASE_PROMPT);
        Assert.assertTrue(injected.contains("记忆条目15"));
        Assert.assertFalse("超出 top-N 的低优先级条目不应注入", injected.contains("记忆条目1\n")
                || injected.endsWith("记忆条目1"));
        // 注入行数 = topN（10）
        int lines = injected.split("\n- \\[").length;
        Assert.assertEquals(MemoryContextInjector.DEFAULT_TOP_N + 1, lines);
    }
}
