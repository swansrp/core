package com.bidr.llm.agent.gate;

import com.bidr.kernel.exception.NoticeException;
import com.bidr.platform.redis.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Title: AgentTaskGateOrphanMillisTest
 * Description: 锁死失联阈值的两件事（10-16 把 orphan-millis 下沉成系统参数时补的针）：
 * <ol>
 *   <li><b>阈值按每次判定现取</b>，两形态（Redis／内存兜底）口径必须一致——否则参数页只在生产生效、
 *       dev 侧改了没反应，而"改一个数要重启"这件事没人会写在 Runbook 里。</li>
 *   <li><b>没到阈值绝不强解</b>：失联判定的另一头是双跑。阈值调小或现取实现写坏了，
 *       代价是两个跑批同时改同一份数据，比"锁残留要等 TTL"严重得多，所以这条必须是负向腿。</li>
 * </ol>
 * 另附向后兼容腿：旧的 {@code long} 构造签名保持固定阈值语义（升级 core 不顶掉没接参数的项目）。
 *
 * @author Sharp
 * @since 2026/10/16
 */
public class AgentTaskGateOrphanMillisTest {

    private static final String PREFIX = "g:";
    private static final String TASK = "t1";
    private static final String LOCK_KEY = PREFIX + TASK;
    private static final String BUSY = "任务正在执行";

    /** 只实现闸门用到的四个方法，其余按返回类型给零值（与 RedisAgentSessionStoreTest 同一手法） */
    private static RedisService fake(Map<String, String> backing) {
        return (RedisService) Proxy.newProxyInstance(
                RedisService.class.getClassLoader(), new Class<?>[]{RedisService.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "get":
                            // 🔴 RedisService 只有 get(String, Class, Class...) 这一把（varargs）⇒ args 恒为 3 个，
                            //    按「长度==2」取键会让读恒返回 null，整条失联腿在空锁上空转（第一版就栽在这里）
                            return args != null && args.length >= 2 ? backing.get((String) args[0]) : null;
                        case "set":
                            if (args != null && args.length == 3) {
                                backing.put((String) args[0], String.valueOf(args[2]));
                            }
                            return null;
                        case "setnx":
                            if (args != null && args.length == 3) {
                                return backing.putIfAbsent((String) args[0], String.valueOf(args[2])) == null;
                            }
                            return Boolean.FALSE;
                        case "delete":
                            if (args != null && args.length == 1) {
                                backing.remove((String) args[0]);
                            }
                            return Boolean.TRUE;
                        case "hasKey":
                            return args != null && args.length == 1 && backing.containsKey((String) args[0]);
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

    private static String ownerOf(Map<String, String> backing, ObjectMapper mapper) throws Exception {
        String json = backing.get(LOCK_KEY);
        Assert.assertNotNull("锁键不在 Redis 里＝这条腿根本没走分布式路径（降级到进程内锁也会「通过」，那是假绿）", json);
        return mapper.readValue(json, GateLock.class).getOwnerToken();
    }

    /** 未过阈值：不得判忙之外的任何动作——B 抢不上、锁仍归 A */
    private static void assertStillOwnedByA(AgentTaskGate gate, Map<String, String> backing, ObjectMapper mapper) throws Exception {
        try {
            gate.acquire(TASK, "ownerB", 60, BUSY);
            Assert.fail("🔴 未过失联阈值却放行第二个属主＝双跑（失联判定的代价方向是数据被两个任务同时改）");
        } catch (NoticeException expected) {
            Assert.assertEquals(BUSY, expected.getMessage());
        }
        Assert.assertEquals("锁仍应归 A", "ownerA", ownerOf(backing, mapper));
    }

    @Test
    public void redis形态失联阈值按每次判定现取() throws Exception {
        Map<String, String> backing = new ConcurrentHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        AtomicLong orphan = new AtomicLong(600000L);
        AgentTaskGate gate = new RedisAgentTaskGate(fake(backing), mapper, PREFIX, orphan::get);

        gate.acquire(TASK, "ownerA", 60, BUSY);
        Assert.assertEquals("抢锁必须真的写进 Redis（否则整条腿在被测降级路径上空转）", "ownerA", ownerOf(backing, mapper));

        assertStillOwnedByA(gate, backing, mapper);
        try {
            gate.checkFree(TASK, BUSY);
            Assert.fail("锁仍在手上，预检必须挡住（预检放行＝同一任务两次派发）");
        } catch (NoticeException expected) {
            Assert.assertEquals(BUSY, expected.getMessage());
        }

        // 参数页把阈值改小＝下一次判定即生效，不重启
        orphan.set(5L);
        Thread.sleep(50L);
        Assert.assertEquals("判定前锁必须还归 A（这条腿的前提；锁没了就说明测的是空表）",
                "ownerA", ownerOf(backing, mapper));
        Assert.assertTrue("阈值改小后应判失联（锁值=" + backing.get(LOCK_KEY) + "）",
                gate.forceUnlockIfOrphan(TASK));
        gate.acquire(TASK, "ownerB", 60, BUSY);
        Assert.assertEquals("失联自愈后锁应易主", "ownerB", ownerOf(backing, mapper));

        // 再改回大值：新属主心跳未超时，不得被误判
        orphan.set(600000L);
        Assert.assertFalse("阈值调回大值后同一次判定不得仍判失联（现取＝读当前值，不是缓存第一次的结果）",
                gate.forceUnlockIfOrphan(TASK));
    }

    @Test
    public void 内存兜底形态与redis同口径() {
        AtomicLong orphan = new AtomicLong(600000L);
        AgentTaskGate gate = new InMemoryAgentTaskGate(orphan::get);

        gate.acquire(TASK, "ownerA", 60, BUSY);
        try {
            gate.acquire(TASK, "ownerB", 60, BUSY);
            Assert.fail("未过阈值不得放行第二个属主");
        } catch (NoticeException expected) {
            Assert.assertEquals(BUSY, expected.getMessage());
        }

        orphan.set(5L);
        try {
            Thread.sleep(50L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Assert.fail("等待被打断，本条腿未跑完");
        }
        Assert.assertTrue("内存形态也必须现取阈值（否则 dev 侧改参数页没反应）", gate.forceUnlockIfOrphan(TASK));
        gate.acquire(TASK, "ownerB", 60, BUSY);
    }

    /** 旧 long 构造签名＝固定阈值语义不变：没接系统参数的项目升级 core 后行为一致 */
    @Test
    public void 固定阈值构造签名两形态都保持原语义() throws Exception {
        Map<String, String> backing = new ConcurrentHashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        AgentTaskGate redisGate = new RedisAgentTaskGate(fake(backing), mapper, PREFIX, 3600000L);
        redisGate.acquire(TASK, "ownerA", 60, BUSY);
        assertStillOwnedByA(redisGate, backing, mapper);

        AgentTaskGate memoryGate = new InMemoryAgentTaskGate(3600000L);
        memoryGate.acquire(TASK, "ownerA", 60, BUSY);
        try {
            memoryGate.acquire(TASK, "ownerB", 60, BUSY);
            Assert.fail("固定大阈值下不得放行第二个属主");
        } catch (NoticeException expected) {
            Assert.assertEquals(BUSY, expected.getMessage());
        }
    }
}
