package com.bidr.llm.agent.session;

import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: RedisAgentSessionStoreTest
 * Description: 🔴 锁死"读失败不得当空表回写"这条数据保全约束。
 * 背景：事件流是整表读-改-写，原实现把 Redis 读失败与"键不存在"一律当空表，
 * 于是一次瞬时失败（实测为中断标志位污染 Redisson 期间）+ 后续写成功，
 * 会把整条历史截断成 1 条并让序号从 1 重启（观测到 64 条→2 条）。
 * <p>
 * 第二批针（10-16）：会话寿命取自供应商而非构造期字段——这样「Agent会话保留(秒)」这类
 * 系统参数改了不用重启；针同时数清"哪些键跟随、停止键为何是例外"。
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
        return fake(backing, failRead, null);
    }

    /**
     * @param ttlWrites 非空时逐次记录每次带 TTL 的写入（键＋秒数），
     *                  用于钉"寿命是每次写入现取、不是构造期固化"，以及"哪些键跟随、哪些键是例外"
     */
    private static RedisService fake(Map<String, String> backing, AtomicBoolean failRead, List<TimedWrite> ttlWrites) {
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
                                recordTtl(ttlWrites, args);
                                backing.put((String) args[0], String.valueOf(args[2]));
                            } else if (args != null && args.length == 2) {
                                backing.put((String) args[0], String.valueOf(args[1]));
                            }
                            return null;
                        case "setnx":
                            if (args != null && args.length == 3) {
                                recordTtl(ttlWrites, args);
                                return !backing.containsKey((String) args[0]);
                            }
                            return Boolean.FALSE;
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

    /** 一次带 TTL 写入的观测：键 + 当次落给 Redis 的秒数 */
    private static final class TimedWrite {
        final String key;
        final int ttlSeconds;

        TimedWrite(String key, int ttlSeconds) {
            this.key = key;
            this.ttlSeconds = ttlSeconds;
        }
    }

    private static void recordTtl(List<TimedWrite> ttlWrites, Object[] args) {
        if (ttlWrites != null && args[1] instanceof Integer) {
            ttlWrites.add(new TimedWrite((String) args[0], (Integer) args[1]));
        }
    }

    /** 按键取最后一次写入的秒数；该键没被写过则报错（防"漏了一个写入点"被静默放过） */
    private static int ttlOf(List<TimedWrite> writes, String key) {
        Integer found = null;
        for (TimedWrite w : writes) {
            if (w.key.equals(key)) {
                found = w.ttlSeconds;
            }
        }
        Assert.assertNotNull("该键没有任何带 TTL 写入，测试假设坏了：" + key);
        return found;
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

    /**
     * 执行实例存活信号必须是独立轻键：心跳线程据此刷新，永不回写状态快照
     * （状态键是整份 JSON 的读-改-写，与 run 线程互踩会丢阶段/live 更新）
     */
    @Test
    public void 存活键独立于状态与事件键() {
        Map<String, String> backing = new ConcurrentHashMap<>();
        RedisAgentSessionStore store = newStore(backing, new AtomicBoolean(false));

        Assert.assertEquals("未心跳应为 0", 0L, store.runTime(SESSION));
        store.touchRun(SESSION);
        long aliveAt = store.runTime(SESSION);
        Assert.assertTrue(aliveAt > 0);
        Assert.assertEquals("🔴 只写轻键：不得产生状态键或事件键",
                false, backing.containsKey(PREFIX + SESSION));
        Assert.assertEquals(false, backing.containsKey(EVENTS_KEY));
        Assert.assertTrue(backing.containsKey(PREFIX + SESSION + ":run"));

        // 收口清理控制键时一并清掉存活键（防残留信号让失联判定失效）
        store.clearControls(SESSION);
        Assert.assertEquals(0L, store.runTime(SESSION));
        Assert.assertEquals(false, backing.containsKey(PREFIX + SESSION + ":run"));
    }

    /**
     * 🔴 系统参数「Agent会话保留(秒)」改了不用重启：寿命必须每次写入现取。
     * 反向证据＝同一 store 两次写同一键记到两个不同秒数——若实现仍把 TTL 当构造期字段固化，
     * 第二次记到的还是 3600，本条报红。
     */
    @Test
    public void 改会话寿命不重启下一次写入即生效() {
        List<TimedWrite> writes = new ArrayList<>();
        AtomicInteger ttl = new AtomicInteger(3600);
        RedisAgentSessionStore store = new RedisAgentSessionStore(
                fake(new ConcurrentHashMap<>(), new AtomicBoolean(false), writes),
                new ObjectMapper(), PREFIX, ttl::get);

        store.appendEvent(SESSION, AgentEvent.LOG, "first");
        Assert.assertEquals(3600, ttlOf(writes, EVENTS_KEY));

        ttl.set(7200);
        store.appendEvent(SESSION, AgentEvent.LOG, "second");
        Assert.assertEquals("🔴 参数改后下一次写入即按新寿命续期（无需重启）",
                7200, ttlOf(writes, EVENTS_KEY));
    }

    /**
     * 跟随参数的写入点必须一次数全（漏一个＝"参数只生效一半"，页面上改了却查不出哪条键还是旧寿命）；
     * 唯一例外＝停止键固定短 TTL：它只是 run 收口前的跨实例传信载体，寿命不该跟会话一起被拉长
     */
    @Test
    public void 全部寿命写入点跟随参数且停止键是固定短TTL例外() {
        List<TimedWrite> writes = new ArrayList<>();
        AtomicInteger ttl = new AtomicInteger(4321);
        RedisAgentSessionStore store = new RedisAgentSessionStore(
                fake(new ConcurrentHashMap<>(), new AtomicBoolean(false), writes),
                new ObjectMapper(), PREFIX, ttl::get);

        AgentSessionState state = new AgentSessionState();
        state.setSessionId(SESSION);
        store.saveState(state);
        store.appendEvent(SESSION, AgentEvent.LOG, "x");
        store.pause(SESSION, "note");
        store.touchView(SESSION);
        store.touchRun(SESSION);
        store.submitAnswer(SESSION, "{}");
        store.resume(SESSION, "guidance");
        store.requestStop(SESSION);

        String[] followingKeys = {PREFIX + SESSION, EVENTS_KEY, PREFIX + SESSION + ":pause",
                PREFIX + SESSION + ":view", PREFIX + SESSION + ":run",
                PREFIX + SESSION + ":answer", PREFIX + SESSION + ":guidance"};
        for (String key : followingKeys) {
            Assert.assertEquals("该键的寿命应现取参数值：" + key, 4321, ttlOf(writes, key));
        }
        Assert.assertEquals("🔴 停止键不吃会话寿命（固定短 TTL；否则一个残留的停止信号能活一整天，"
                + "下次 run 开局即被判停）", 600, ttlOf(writes, PREFIX + SESSION + ":stop"));
    }

    /** 旧构造签名（int 常量）语义不变＝整个生命周期固定：升级 core 不会顶掉没接参数的项目 */
    @Test
    public void 整数构造签名保持固定寿命语义() {
        List<TimedWrite> writes = new ArrayList<>();
        RedisAgentSessionStore store = new RedisAgentSessionStore(
                fake(new ConcurrentHashMap<>(), new AtomicBoolean(false), writes),
                new ObjectMapper(), PREFIX, 1800);
        store.appendEvent(SESSION, AgentEvent.LOG, "a");
        store.appendEvent(SESSION, AgentEvent.LOG, "b");
        Assert.assertEquals(1800, ttlOf(writes, EVENTS_KEY));
    }
}
