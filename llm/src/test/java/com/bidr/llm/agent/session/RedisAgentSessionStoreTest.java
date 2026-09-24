package com.bidr.llm.agent.session;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Title: RedisAgentSessionStoreTest
 * Description: 🔴 锁死"读失败不得当空表回写"这条数据保全约束。
 * 背景：事件流是整表读-改-写，原实现把 Redis 读失败与"键不存在"一律当空表，
 * 于是一次瞬时失败（实测为中断标志位污染 Redisson 期间）+ 后续写成功，
 * 会把整条历史截断成 1 条并让序号从 1 重启（观测到 64 条→2 条）。
 *
 * @author sharp
 * @since 2026/9/24
 */
public class RedisAgentSessionStoreTest {

    private static final String PREFIX = "t:";
    private static final String SESSION = "s1";
    private static final String EVENTS_KEY = PREFIX + SESSION + ":events";

    /** 可编程失败的 RedisService 假实现（动态代理，不引额外 mock 依赖） */
    private static RedisService fake(Map<String, String> backing, AtomicBoolean failRead) {
        return (RedisService) Proxy.newProxyInstance(
                RedisService.class.getClassLoader(), new Class<?>[]{RedisService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "get":
                            if (failRead.get()) {
                                throw new IllegalStateException("simulated redis read failure");
                            }
                            return args == null || args.length == 0 ? null : backing.get((String) args[0]);
                        case "set":
                            if (args != null && args.length == 3) {
                                backing.put((String) args[0], String.valueOf(args[2]));
                            } else if (args != null && args.length == 2) {
                                backing.put((String) args[0], String.valueOf(args[1]));
                            }
                            return null;
                        case "delete":
                            if (args != null && args.length == 1) {
                                backing.remove((String) args[0]);
                            }
                            return Boolean.TRUE;
                        case "hasKey":
                            return Boolean.FALSE;
                        default:
                            Class<?> rt = method.getReturnType();
                            if (rt == boolean.class || rt == Boolean.class) {
                                return Boolean.FALSE;
                            }
                            if (rt == long.class || rt == Long.class) {
                                return 0L;
                            }
                            if (rt == int.class || rt == Integer.class) {
                                return 0;
                            }
                            if (rt == double.class || rt == Double.class) {
                                return 0d;
                            }
                            return null;
                    }
                });
    }

    private static RedisAgentSessionStore newStore(Map<String, String> backing, AtomicBoolean failRead) {
        return new RedisAgentSessionStore(fake(backing, failRead), new ObjectMapper(), PREFIX, 3600);
    }

    @Test
    public void 读失败时跳过追加且绝不回写空表() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        AtomicBoolean failRead = new AtomicBoolean(false);
        RedisAgentSessionStore store = newStore(backing, failRead);

        // 先正常落 3 条历史
        for (int i = 0; i < 3; i++) {
            Assert.assertTrue(store.appendEvent(SESSION, AgentEvent.LOG, "line" + i) > 0);
        }
        String before = backing.get(EVENTS_KEY);
        Assert.assertNotNull(before);

        // 模拟瞬时读失败：不得把历史截断
        failRead.set(true);
        long seq = store.appendEvent(SESSION, AgentEvent.LOG, "during-failure");

        Assert.assertEquals("读失败应返回负值表示未落库", -1L, seq);
        Assert.assertEquals("🔴 历史必须原样保留（不得被空表覆盖）", before, backing.get(EVENTS_KEY));

        // 恢复后：历史仍在（读失败期间 events() 同样读不到，故此处先解除故障再校验）
        failRead.set(false);
        Assert.assertEquals(3, store.events(SESSION, 0).size());
        long next = store.appendEvent(SESSION, AgentEvent.LOG, "after-recovery");
        Assert.assertEquals("恢复后序号应续接历史", 4L, next);
        Assert.assertEquals(4, store.events(SESSION, 0).size());
    }

    @Test
    public void 首次追加从序号一开始() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        RedisAgentSessionStore store = newStore(backing, new AtomicBoolean(false));
        Assert.assertEquals(1L, store.appendEvent(SESSION, AgentEvent.RUN_START, "start"));
        Assert.assertEquals(2L, store.appendEvent(SESSION, AgentEvent.LOG, "log"));
    }
}
