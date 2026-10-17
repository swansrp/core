package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.config.db.TenantGateSuppressor;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Title: BindRepoGateSuppressTest
 * Description: {@code @BindRepo} 族取数的"翻译读旁路"守卫——纯 JUnit4＋Mockito，不起 Spring、不连库。
 * <p>机制：绑定回填是「引用值 → 展示值」的翻译，不是业务读集。目标实体批查若被租户数据闸改写，
 * 跨租户引用（如平台运营建的单据 create_by）会被静默翻译成空白。故三条取数通道的批查一律包在
 * {@link TenantGateSuppressor#runSuppressed} 的调用帧里，闸在 authorization 侧反向读这个 kernel 位
 * （依赖方向 kernel←authorization，写入方够不着闸本体）。</p>
 * <ul>
 * <li>腿 1：batchConvert 的批查落在旁路帧内（查询时刻采样为 true），帧外恢复 false；</li>
 * <li>腿 2：负对照——同一 mock 在帧外被直接调用 ⇒ 采样 false（证明腿 1 不是恒真）；</li>
 * <li>腿 3：异常腿——select 抛穿时旁路位必须恢复，免闸态不得漏在线程上；</li>
 * <li>腿 4：嵌套腿——内层退出后外层态不被吞（恢复前值而非无条件清）；</li>
 * <li>腿 5：结构针——BindRepoHandler 源码里"反射 select"站点数＝"runSuppressed 包裹"站点数＝3，
 * 计数函数自带合成正/负对照（防恒 0 空转）。</li>
 * </ul>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-10-17
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class BindRepoGateSuppressTest {

    /** 每次 select 真正发生时采样一次旁路位（下标即调用顺序） */
    private static final List<Boolean> SAMPLES_AT_SELECT = Collections.synchronizedList(new ArrayList<>());

    private static BaseSqlRepo boundRepo;

    /** 目标实体：code 做匹配列、name 做提取列；刻意不带 valid 字段（少一列谓词，判据更干净） */
    public static class Bound {
        private String code;
        private String name;

        public String getCode() {
            return code;
        }

        public String getName() {
            return name;
        }
    }

    /** 源 VO：字段上的注解即被测配置 */
    public static class Vo {
        @BindRepo(entity = Bound.class, matchField = "code", extractField = "name")
        private String label;
    }

    private static Bound bound(String code, String name) {
        Bound b = new Bound();
        b.code = code;
        b.name = name;
        return b;
    }

    /** 采样型 mock：select 被真正调用时记录当时的旁路位，可选抛穿 */
    private static BaseSqlRepo samplingRepo(List<?> data, RuntimeException toThrow) {
        BaseSqlRepo repo = Mockito.mock(BaseSqlRepo.class);
        Mockito.when(repo.getEntityClass()).thenReturn(Bound.class);
        Mockito.when(repo.select(Mockito.any(Wrapper.class))).thenAnswer(inv -> {
            SAMPLES_AT_SELECT.add(TenantGateSuppressor.isSuppressed());
            if (toThrow != null) {
                throw toThrow;
            }
            return data;
        });
        return repo;
    }

    private static void useRepos(BaseSqlRepo... repos) {
        String[] names = new String[repos.length];
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        for (int i = 0; i < repos.length; i++) {
            names[i] = "repo" + i;
            Mockito.when(ctx.getBean(names[i])).thenReturn(repos[i]);
        }
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class)).thenReturn(names);
        BeanUtil.setContext(ctx);
    }

    @BeforeClass
    public static void setUpContext() {
        boundRepo = samplingRepo(Arrays.asList(bound("u1", "张三"), bound("u2", "李四")), null);
        useRepos(boundRepo);
    }

    @Before
    public void resetSamples() {
        SAMPLES_AT_SELECT.clear();
        Assert.assertFalse("进测试前本线程不得残留旁路态（否则腿 2 空转）",
                TenantGateSuppressor.isSuppressed());
    }

    private static BindRepo annotation() throws Exception {
        Field field = Vo.class.getDeclaredField("label");
        BindRepo annotation = field.getAnnotation(BindRepo.class);
        Assert.assertNotNull("夹具字段必须带 @BindRepo", annotation);
        return annotation;
    }

    private static Set<Object> sourceValues() {
        return new LinkedHashSet<>(Arrays.asList((Object) "u1", "u2"));
    }

    /** 腿 1：batchConvert 的批查必须在旁路帧内发生，且帧外立刻恢复 */
    @Test
    public void batchConvertQueriesInsideSuppressedFrame() throws Exception {
        Map<Object, Object> mapped = new BindRepoHandler().batchConvert(annotation(), sourceValues());

        Assert.assertEquals("批查必须发生一次，实际采样: " + SAMPLES_AT_SELECT, 1, SAMPLES_AT_SELECT.size());
        Assert.assertTrue("翻译读的 select 必须在旁路帧内", SAMPLES_AT_SELECT.get(0));
        Assert.assertFalse("帧外旁路位必须已恢复", TenantGateSuppressor.isSuppressed());
        Assert.assertEquals("张三", mapped.get("u1"));
        Assert.assertEquals("李四", mapped.get("u2"));
    }

    /** 腿 2（负对照）：同一 mock 在帧外被直接调用 ⇒ 采样必为 false，证明腿 1 不是恒真 */
    @Test
    public void sameQueryOutsideFrameIsNotSuppressed() {
        boundRepo.select(new QueryWrapper<>());
        Assert.assertEquals("直接调用必须落进采样本体（腿失效＝夹具没跑起来）", 1, SAMPLES_AT_SELECT.size());
        Assert.assertFalse("帧外不得为旁路态（腿 1 的负对照）", SAMPLES_AT_SELECT.get(0));
    }

    /** 腿 3：select 抛穿时 handler 通道也必须恢复旁路位（finally 腿，不是只在正常路径生效） */
    @Test
    public void suppressedFrameIsRestoredWhenQueryThrows() throws Exception {
        BaseSqlRepo failing = samplingRepo(Collections.emptyList(), new IllegalStateException("boom"));
        useRepos(failing);
        try {
            new BindRepoHandler().batchConvert(annotation(), sourceValues());
            Assert.fail("select 抛穿必须冒到 batchConvert");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("boom", expected.getMessage());
        }
        Assert.assertEquals("抛穿前必须已在帧内采样一次", Arrays.asList(Boolean.TRUE), SAMPLES_AT_SELECT);
        Assert.assertFalse("异常腿之后旁路位必须已恢复", TenantGateSuppressor.isSuppressed());
        useRepos(boundRepo);
    }

    /** 腿 4：嵌套帧退出后外层态不得被吞——恢复的是前值，不是清位 */
    @Test
    public void nestedFramesRestoreOuterState() {
        String observed = TenantGateSuppressor.runSuppressed(() ->
                TenantGateSuppressor.runSuppressed(() -> "inner") + "|outer:"
                        + TenantGateSuppressor.isSuppressed());
        Assert.assertEquals("inner|outer:true", observed);
        Assert.assertFalse("最外层帧退出后必须彻底恢复", TenantGateSuppressor.isSuppressed());
    }

    /** 腿 5：结构针——反射 select 站点数与旁路帧包裹数必须一一对应且＝3（覆盖 list/condition 两条通道） */
    @Test
    public void everySelectSiteIsWrappedBySuppressor() throws IOException {
        String source = new String(Files.readAllBytes(kernelModuleRoot()
                .resolve("src/main/java/com/bidr/kernel/config/response/BindRepoHandler.java")),
                StandardCharsets.UTF_8);
        int invokeSites = countOccurrences(source, "ReflectionUtil.invoke(repo, selectMethod, wrapper)");
        int wrapSites = countOccurrences(source, "TenantGateSuppressor.runSuppressed(");
        Assert.assertEquals("三条取数通道的批查必须全部包进旁路帧", 3, invokeSites);
        Assert.assertEquals("旁路帧包裹数必须与 select 站点数一致", invokeSites, wrapSites);
        // 计数函数自证（防恒 0 空转）
        Assert.assertEquals("计数函数失效", 2, countOccurrences("a runSuppressed( b runSuppressed(",
                "runSuppressed("));
        Assert.assertEquals("计数函数负对照失效", 0, countOccurrences("nothing here", "runSuppressed("));
    }

    private static int countOccurrences(String content, String needle) {
        int count = 0;
        int idx = content.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = content.indexOf(needle, idx + needle.length());
        }
        return count;
    }

    /** kernel 模块根定位（目录名＋pom.xml＋src/main/java，定位失败直接报红，拒绝空扫描假绿） */
    private static Path kernelModuleRoot() {
        Path start = Paths.get(System.getProperty("basedir", System.getProperty("user.dir")))
                .toAbsolutePath().normalize();
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            if ("kernel".equals(dir.getFileName().toString())
                    && Files.isRegularFile(dir.resolve("pom.xml"))
                    && Files.isDirectory(dir.resolve("src").resolve("main").resolve("java"))) {
                return dir;
            }
        }
        throw new IllegalStateException("无法从 " + start + " 定位 core/kernel 模块根");
    }
}
