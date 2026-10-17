package com.bidr.llm.config;

import com.bidr.kernel.constant.param.Param;
import com.bidr.llm.constant.param.LlmParam;
import com.bidr.platform.service.cache.SysConfigCacheService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Title: LlmRuntimeParamResolutionTest
 * Description: 钉死"库值 → yml 地板 → core 默认"这条取值链的回落面。
 * 这一层是运行期参数唯一的失效保险：管理页上填进去的是什么都不能让装配炸掉，
 * 也不能静默变成 0 或负数（setex 拿到 0/负 TTL 是当场报错，拿到超大值是键永不过期，
 * 两种坏法都发生在跑批中间、归因极难）。所以非法值一律回落，且回落要现读、不缓存副本。
 *
 * @author Sharp
 * @since 2026/10/16
 */
public class LlmRuntimeParamResolutionTest {

    /** 库值假实现：只喂 getSysConfigValue(Param)，读次数用于证明"每次现读、无缓存副本" */
    private static class FakeSysConfig extends SysConfigCacheService {
        private final AtomicReference<String> value;
        private final AtomicInteger reads = new AtomicInteger();
        private boolean throwOnRead = false;

        FakeSysConfig(AtomicReference<String> value) {
            this.value = value;
        }

        @Override
        public String getSysConfigValue(Param param) {
            reads.incrementAndGet();
            if (throwOnRead) {
                throw new IllegalStateException("simulated: 参数不存在");
            }
            return value.get();
        }
    }

    private static ObjectProvider<SysConfigCacheService> provider(SysConfigCacheService service) {
        return new ObjectProvider<SysConfigCacheService>() {
            @Override
            public SysConfigCacheService getObject() {
                return service;
            }

            @Override
            public SysConfigCacheService getObject(Object... args) {
                return service;
            }

            @Override
            public SysConfigCacheService getIfAvailable() {
                return service;
            }

            @Override
            public SysConfigCacheService getIfUnique() {
                return service;
            }
        };
    }

    @Test
    public void 库值是合法正数时压过yml地板() {
        AtomicReference<String> raw = new AtomicReference<>("172800");
        long got = AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_SESSION_TTL_SECONDS,
                provider(new FakeSysConfig(raw)), 86400L);
        Assert.assertEquals(172800L, got);
    }

    @Test
    public void 出厂留空按yml地板且带空格的空串同样回落() {
        for (String blank : new String[]{null, "", "   "}) {
            long got = AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS,
                    provider(new FakeSysConfig(new AtomicReference<>(blank))), 90000L);
            Assert.assertEquals("空值必须回落地板（出厂就是留空）：" + blank, 90000L, got);
        }
    }

    @Test
    public void 非数字与非正数一律回落不抛给调用方() {
        for (String bad : new String[]{"abc", "86400s", "0", "-1", "9.5", ""}) {
            long got = AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_SESSION_TTL_SECONDS,
                    provider(new FakeSysConfig(new AtomicReference<>(bad))), 86400L);
            Assert.assertEquals("非法值「" + bad + "」必须回落地板而不是炸在装配或写入路径上", 86400L, got);
        }
    }

    @Test
    public void 读库值抛异常时回落() {
        FakeSysConfig service = new FakeSysConfig(new AtomicReference<>("172800"));
        service.throwOnRead = true;
        long got = AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_SESSION_TTL_SECONDS,
                provider(service), 86400L);
        Assert.assertEquals(86400L, got);
    }

    /** platform 是 optional 依赖：没接入（provider 给不出 bean，甚至 provider 本身为 null）也只能回落，不能 NoClassDefFound／NPE */
    @Test
    public void 系统参数服务缺席时回落() {
        long got = AgentSessionAutoConfiguration.positiveLong(LlmParam.AGENT_SESSION_TTL_SECONDS,
                provider(null), 86400L);
        Assert.assertEquals(86400L, got);
        Assert.assertEquals(86400L, AgentSessionAutoConfiguration.positiveLong(
                LlmParam.AGENT_SESSION_TTL_SECONDS, null, 86400L));
        Assert.assertEquals(90000, AgentSessionAutoConfiguration.positiveInt(
                LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS, null, 90000));
    }

    /** 🔴 每次调用现读：管理页改完不用重启，取值就是新值（本条是"下沉成运行期参数"的全部意义） */
    @Test
    public void 每次现读不缓存副本改完下一次即生效() {
        AtomicReference<String> raw = new AtomicReference<>("3600");
        FakeSysConfig service = new FakeSysConfig(raw);
        ObjectProvider<SysConfigCacheService> provider = provider(service);

        Assert.assertEquals(3600L, AgentSessionAutoConfiguration.positiveLong(
                LlmParam.AGENT_SESSION_TTL_SECONDS, provider, 86400L));
        raw.set("7200");
        Assert.assertEquals("🔴 改值后下一次取值必须就是新值（存在缓存副本＝这条腿要报红）",
                7200L, AgentSessionAutoConfiguration.positiveLong(
                        LlmParam.AGENT_SESSION_TTL_SECONDS, provider, 86400L));
        Assert.assertEquals("取值必须真的读了参数服务两次", 2, service.reads.get());
    }

    /** int 形态（Redis TTL 入参）：超出 int 可表示范围一律回落地板——越界几乎必然是单位填错，收口上界＝约 68 年的永生键 */
    @Test
    public void 超int域一律回落地板() {
        // 按毫秒口径误填＝"一天"填成 86400000 秒＝2.7 年：数值仍在 int 内，无人能在这一层拦住（如实透传，代价记在参数说明里）
        FakeSysConfig inRange = new FakeSysConfig(new AtomicReference<>("86400000"));
        Assert.assertEquals(86400000, AgentSessionAutoConfiguration.positiveInt(
                LlmParam.AGENT_SESSION_TTL_SECONDS, provider(inRange), 86400));

        // 真越界（超出 int 可表示范围）⇒ 与其余非法形态同处置＝回落地板；不收口到 Integer.MAX_VALUE（回落值恒在域内，setex 不会拿到越界值）
        FakeSysConfig overflow = new FakeSysConfig(new AtomicReference<>("99999999999"));
        Assert.assertEquals("超出 int 可表示范围必须回落地板，而不是按 2147483647（约 68 年）生效",
                86400, AgentSessionAutoConfiguration.positiveInt(
                        LlmParam.AGENT_SESSION_TTL_SECONDS, provider(overflow), 86400));
    }

    /** 出厂值必须是留空：给了默认数值就等于"参数页一有值就顶掉现场 yml"，那是改共享件的默认行为 */
    @Test
    public void 两枚新键出厂留空() {
        Assert.assertEquals("", LlmParam.AGENT_SESSION_TTL_SECONDS.getDefaultValue());
        Assert.assertEquals("", LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS.getDefaultValue());
    }

    /**
     * 🔴 活针：证明「bean 解析本身抛」这条回落路径真的被走到的那条腿——
     * 若 positiveLong 把 getIfAvailable() 裸在 try 外，这里会把异常透传出来（写路径当场炸），而不是回落地板。
     */
    @Test
    public void 参数服务解析抛异常时回落不传染写路径() {
        ObjectProvider<SysConfigCacheService> boom = new ObjectProvider<SysConfigCacheService>() {
            @Override
            public SysConfigCacheService getObject() {
                throw new IllegalStateException("simulated: bean 解析失败");
            }

            @Override
            public SysConfigCacheService getObject(Object... args) {
                throw new IllegalStateException("simulated: bean 解析失败");
            }

            @Override
            public SysConfigCacheService getIfAvailable() {
                throw new IllegalStateException("simulated: bean 解析失败");
            }

            @Override
            public SysConfigCacheService getIfUnique() {
                throw new IllegalStateException("simulated: bean 解析失败");
            }
        };
        Assert.assertEquals(86400L, AgentSessionAutoConfiguration.positiveLong(
                LlmParam.AGENT_SESSION_TTL_SECONDS, boom, 86400L));
        Assert.assertEquals(90000, AgentSessionAutoConfiguration.positiveInt(
                LlmParam.AGENT_TASK_GATE_ORPHAN_MILLIS, boom, 90000));
    }
}
