package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Title: BindRepoResolverSpiTest
 * Description: {@link BindRepoResolver} SPI 兜底链单测（T2.3c/T2.3d：@BindUser/@BindDept 占位实体解耦）——
 * 纯 JUnit4＋Mockito，不起 Spring、不连库。
 * <ul>
 *     <li>腿 1（SPI 兜底命中）：占位实体（模拟 UserRef）repoIndex miss → SPI 解析为 fake repo＋有效实体，
 *         batchConvert 走通列解析/回填，且列名按<b>有效实体类</b>解析（customer_number 而非占位类驼峰转下划线）；</li>
 *     <li>腿 1b（解析缓存）：同一占位类第二次转换不再调用 resolver（按注解声明类缓存，先例 conditionCache）；</li>
 *     <li>腿 2（无解析器）：容器内零 BindRepoResolver Bean ⇒ 维持既有 log.warn＋跳过（返回空 Map，不抛）；</li>
 *     <li>腿 3（精确匹配优先）：显式 entity 直写（模拟 AcUser.class）走 repoIndex 精确命中，
 *         计数 stub 证明 SPI 的 resolve() 全程未被调用——既有显式 entity 用例零变化。</li>
 * </ul>
 * Copyright: Copyright (c) 2026 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2026-11-03
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class BindRepoResolverSpiTest {

    // ======================== 夹具实体 ========================

    /** 占位标记类（模拟 authorization-api 的 UserRef）：无表映射、无 BaseSqlRepo，repoIndex 必 miss */
    public static final class UserRefLike {
    }

    /** 有效实体（模拟 AcUser）：带 valid 字段（占位类没有），钉死「existedField/列名按有效实体类判定」 */
    public static class UserLike {
        @TableField(value = "customer_number")
        private String customerNumber;
        @TableId(value = "user_name_col")
        private String name;
        @TableField(value = "`valid`")
        private String valid;

        public String getCustomerNumber() {
            return customerNumber;
        }

        public String getName() {
            return name;
        }

        public String getValid() {
            return valid;
        }
    }

    /** 腿 1 夹具 VO：占位实体直写 @BindRepo（等价 @BindUser 元注解桥接后的合成形态） */
    private static class VoPlaceholder {
        @BindRepo(entity = UserRefLike.class, matchField = "customerNumber", extractField = "name")
        private String userName;
    }

    /** 腿 3 夹具 VO：显式有效实体直写（既有用法形态） */
    private static class VoExplicit {
        @BindRepo(entity = UserLike.class, matchField = "customerNumber", extractField = "name")
        private String userName;
    }

    // ======================== 夹具工厂 ========================

    private static UserLike user(String customerNumber, String name) {
        UserLike u = new UserLike();
        u.customerNumber = customerNumber;
        u.name = name;
        u.valid = "1";
        return u;
    }

    /** fake repo：Mockito mock BaseSqlRepo，select 返回预制行（可捕获 wrapper 供列名断言） */
    private static BaseSqlRepo fakeRepo(Class<?> entityClass, List<?> data, List<QueryWrapper> wrapperSink) {
        BaseSqlRepo repo = Mockito.mock(BaseSqlRepo.class);
        Mockito.when(repo.getEntityClass()).thenReturn(entityClass);
        Mockito.when(repo.select(Mockito.any(Wrapper.class))).thenAnswer(inv -> {
            if (wrapperSink != null) {
                wrapperSink.add((QueryWrapper) inv.getArgument(0));
            }
            return data;
        });
        return repo;
    }

    /** 计数型 resolver：记录 resolve() 调用次数（腿 1b 缓存断言 / 腿 3 不被调用断言） */
    private static class CountingResolver implements BindRepoResolver {
        final AtomicInteger calls = new AtomicInteger();
        final BindRepoResolver delegate;

        CountingResolver(BindRepoResolver delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<BindTarget> resolve(Class<?> entityClass) {
            calls.incrementAndGet();
            return delegate.resolve(entityClass);
        }
    }

    /** UserRefLike → fake repo(UserLike) 的固定解析 */
    private static BindRepoResolver userRefResolver(BaseSqlRepo repo) {
        return entityClass -> entityClass == UserRefLike.class
                ? Optional.of(new BindRepoResolver.BindTarget(repo, UserLike.class))
                : Optional.empty();
    }

    /**
     * 换容器：按本腿需要注入 BaseSqlRepo beans 与 BindRepoResolver beans。
     * （每腿 new BindRepoHandler()，其 resolvers/targetCache 实例级延迟初始化，换 ctx 即换世界）
     */
    private static void useContext(BaseSqlRepo[] repos, BindRepoResolver[] resolvers) {
        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        String[] repoNames = new String[repos.length];
        for (int i = 0; i < repos.length; i++) {
            repoNames[i] = "repo" + i;
            Mockito.when(ctx.getBean(repoNames[i])).thenReturn(repos[i]);
        }
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class)).thenReturn(repoNames);
        String[] resolverNames = new String[resolvers.length];
        for (int i = 0; i < resolvers.length; i++) {
            resolverNames[i] = "resolver" + i;
            Mockito.when(ctx.getBean(resolverNames[i])).thenReturn(resolvers[i]);
        }
        Mockito.when(ctx.getBeanNamesForType(BindRepoResolver.class)).thenReturn(resolverNames);
        BeanUtil.setContext(ctx);
    }

    @Before
    public void resetWorld() {
        useContext(new BaseSqlRepo[0], new BindRepoResolver[0]);
    }

    private static BindRepo annotationOf(Class<?> voClass) throws Exception {
        Field field = voClass.getDeclaredField("userName");
        BindRepo annotation = field.getAnnotation(BindRepo.class);
        Assert.assertNotNull("夹具字段必须带 @BindRepo", annotation);
        return annotation;
    }

    private static Set<Object> sourceValues() {
        return new LinkedHashSet<>(Arrays.asList((Object) "u1", "u2"));
    }

    // ======================== 腿 1：SPI 兜底命中（占位→fake repo，列解析/回填走通） ========================

    @Test
    public void spiFallbackResolvesPlaceholderAndFillsNames() throws Exception {
        List<QueryWrapper> wrappers = new ArrayList<>();
        BaseSqlRepo repo = fakeRepo(UserLike.class,
                Arrays.asList(user("u1", "张三"), user("u2", "李四")), wrappers);
        CountingResolver resolver = new CountingResolver(userRefResolver(repo));
        useContext(new BaseSqlRepo[0], new BindRepoResolver[]{resolver});

        Map<Object, Object> mapped = new BindRepoHandler().batchConvert(
                annotationOf(VoPlaceholder.class), sourceValues());

        Assert.assertEquals("占位实体经 SPI 兜底必须回填成功", "张三", mapped.get("u1"));
        Assert.assertEquals("李四", mapped.get("u2"));
        Assert.assertEquals("批查必须发生一次", 1, wrappers.size());
        // 有效实体类钉死：占位类 UserRefLike 无任何字段——若 existedField/列解析误用占位类，
        // 则 valid 谓词不会追加（existedField=false）且列名走驼峰兜底；两条断言合证解析用的是 UserLike
        String sqlSegment = wrappers.get(0).getSqlSegment();
        Assert.assertTrue("IN 列必须按有效实体类解析为 customer_number，实际: " + sqlSegment,
                sqlSegment.contains("customer_number"));
        Assert.assertTrue("valid 谓词必须按有效实体类判定追加，实际: " + sqlSegment,
                sqlSegment.contains("valid"));
    }

    // ======================== 腿 1b：解析结果按注解声明类缓存（不重复遍历 SPI） ========================

    @Test
    public void resolvedTargetIsCachedPerDeclaredClass() throws Exception {
        BaseSqlRepo repo = fakeRepo(UserLike.class,
                Arrays.asList(user("u1", "张三"), user("u2", "李四")), null);
        CountingResolver resolver = new CountingResolver(userRefResolver(repo));
        useContext(new BaseSqlRepo[0], new BindRepoResolver[]{resolver});
        BindRepoHandler handler = new BindRepoHandler();

        handler.batchConvert(annotationOf(VoPlaceholder.class), sourceValues());
        handler.batchConvert(annotationOf(VoPlaceholder.class), sourceValues());

        Assert.assertEquals("第二次转换必须命中缓存，resolver.resolve 只允许调用一次",
                1, resolver.calls.get());
    }

    // ======================== 腿 2：无解析器 ⇒ 维持 warn＋跳过（不抛） ========================

    @Test
    public void unresolvedPlaceholderWarnsAndSkipsWithoutThrowing() throws Exception {
        useContext(new BaseSqlRepo[0], new BindRepoResolver[0]);

        Map<Object, Object> mapped = new BindRepoHandler().batchConvert(
                annotationOf(VoPlaceholder.class), sourceValues());

        Assert.assertNotNull("无解析器时不得抛异常（warn＋跳过语义）", mapped);
        Assert.assertTrue("无解析器时必须返回空 Map（跳过绑定）", mapped.isEmpty());
    }

    // ======================== 腿 3：显式 entity 走 repoIndex 精确匹配，SPI 不被调用 ========================

    @Test
    public void explicitEntityHitsRepoIndexWithoutInvokingSpi() throws Exception {
        List<QueryWrapper> wrappers = new ArrayList<>();
        BaseSqlRepo repo = fakeRepo(UserLike.class,
                Arrays.asList(user("u1", "张三"), user("u2", "李四")), wrappers);
        CountingResolver resolver = new CountingResolver(userRefResolver(repo));
        useContext(new BaseSqlRepo[]{repo}, new BindRepoResolver[]{resolver});

        Map<Object, Object> mapped = new BindRepoHandler().batchConvert(
                annotationOf(VoExplicit.class), sourceValues());

        Assert.assertEquals("既有显式 entity 用例行为零变化：u1", "张三", mapped.get("u1"));
        Assert.assertEquals("既有显式 entity 用例行为零变化：u2", "李四", mapped.get("u2"));
        Assert.assertEquals("精确匹配必须命中，SPI resolve() 不得被调用", 0, resolver.calls.get());
    }

    // ======================== 腿 3b：List 通道（selectIn）同链路兜底（顺带覆盖第二条通道） ========================

    @Test
    public void listChannelAlsoResolvesThroughSpiFallback() throws Exception {
        List<QueryWrapper> wrappers = new ArrayList<>();
        BaseSqlRepo repo = fakeRepo(UserLike.class,
                Arrays.asList(user("u1", "张三"), user("u1", "张三备份")), wrappers);
        CountingResolver resolver = new CountingResolver(userRefResolver(repo));
        useContext(new BaseSqlRepo[0], new BindRepoResolver[]{resolver});

        Map<Object, List<Object>> grouped = new BindRepoHandler().batchConvertList(
                annotationOf(VoPlaceholder.class), sourceValues());

        Assert.assertEquals("List 通道按 SPI 兜底分组：u1 两组", 2, grouped.get("u1").size());
        Assert.assertNull("u2 无行不建键", grouped.get("u2"));
        Assert.assertTrue("List 通道 IN 列同样按有效实体类解析",
                wrappers.get(0).getSqlSegment().contains("customer_number"));
    }
}
