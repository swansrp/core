package com.bidr.kernel.config.response;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.utils.BeanUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Title: BindRepoConditionTest
 * Description: {@code @BindRepo(condition=...)} 关联表达式引擎单测（泛化多表 join，取代早期 mid* 硬编码）。
 * <p>
 * 覆盖算法核心：单表等值、多字段复合源键、单中间表一对多、多级 join（this→B→C→目标）、
 * 一对多扇出、无匹配返回空、非法 condition 不抛异常；以及四种字段形态分派
 * （= BindField / BindEntity / BindFieldList / BindEntityList，场景 7–10 覆盖后三种）。
 * <p>
 * 纯单测：mock WebApplicationContext 注入 BeanUtil，mock BaseSqlRepo 数据源，不连数据库。
 * mock 的 select(Wrapper) 忽略 IN 条件返回全部 fixture，实际过滤由 Handler 的组合键匹配逻辑完成。
 *
 * @author Sharp
 * @since 2026/09/08
 */
public class BindRepoConditionTest {

    @SuppressWarnings("rawtypes")
    private static BaseSqlRepo repo(Class<?> entityClass, List<?> data) {
        BaseSqlRepo r = Mockito.mock(BaseSqlRepo.class);
        Mockito.when(r.getEntityClass()).thenReturn(entityClass);
        Mockito.when(r.select(Mockito.any(Wrapper.class))).thenAnswer(inv -> data);
        return r;
    }

    @BeforeClass
    @SuppressWarnings("unchecked")
    public static void setUp() {
        Map<String, BaseSqlRepo> repos = new HashMap<>();
        repos.put("tgtRepo", repo(Tgt.class, Arrays.asList(tgt(1L, "one"), tgt(2L, "two"))));
        repos.put("tgt2Repo", repo(Tgt2.class, Arrays.asList(tgt2("p", 10L, "L1"), tgt2("q", 20L, "L2"))));
        repos.put("midRepo", repo(Mid.class, Arrays.asList(mid(1L, 100L), mid(1L, 101L), mid(2L, 200L))));
        repos.put("tgt3Repo", repo(Tgt3.class, Arrays.asList(tgt3(100L, "a"), tgt3(101L, "b"), tgt3(200L, "c"))));
        repos.put("bRepo", repo(B.class, Arrays.asList(b(1L, 5L), b(7L, 8L))));
        repos.put("cRepo", repo(C.class, Arrays.asList(c(5L, 9L), c(8L, 11L))));
        repos.put("dRepo", repo(D.class, Arrays.asList(d(9L, "deep"), d(11L, "deep2"))));

        WebApplicationContext ctx = Mockito.mock(WebApplicationContext.class);
        Mockito.when(ctx.getBeanNamesForType(BindRepoHandler.class)).thenReturn(new String[]{"bindRepoHandler"});
        Mockito.when(ctx.getBeanNamesForType(BaseSqlRepo.class)).thenReturn(repos.keySet().toArray(new String[0]));
        Mockito.when(ctx.getBean("bindRepoHandler")).thenReturn(new BindRepoHandler());
        for (Map.Entry<String, BaseSqlRepo> e : repos.entrySet()) {
            Mockito.when(ctx.getBean(e.getKey())).thenReturn(e.getValue());
        }
        BeanUtil.setContext(ctx);
    }

    // ======================== 场景 1：单表等值（非 List，取 extractField） ========================

    @Test
    public void singleTableEqualityFillsExtractField() {
        List<Vo1> list = new ArrayList<>(Arrays.asList(vo1(1L), vo1(2L)));
        RespConvert.customBindConvert(list, Vo1.class);
        Assert.assertEquals("one", list.get(0).getName());
        Assert.assertEquals("two", list.get(1).getName());
    }

    // ======================== 场景 2：多字段复合源键 ========================

    @Test
    public void compositeSourceKeyMatches() {
        List<Vo2> list = new ArrayList<>(Arrays.asList(vo2("p", 10L), vo2("q", 20L)));
        RespConvert.customBindConvert(list, Vo2.class);
        Assert.assertEquals("L1", list.get(0).getLabel());
        Assert.assertEquals("L2", list.get(1).getLabel());
    }

    // ======================== 场景 3：单中间表一对多（含扇出） ========================

    @Test
    public void singleMidTableOneToManyFansOut() {
        List<Vo3> list = new ArrayList<>(Arrays.asList(vo3(1L), vo3(2L)));
        RespConvert.customBindConvert(list, Vo3.class);
        // id=1 经 Mid(1,100)/Mid(1,101) 扇出到 Tgt3 a、b
        List<Tgt3> items0 = list.get(0).getItems();
        Assert.assertEquals(2, items0.size());
        Assert.assertEquals("a", items0.get(0).getName());
        Assert.assertEquals("b", items0.get(1).getName());
        // id=2 经 Mid(2,200) 命中 Tgt3 c
        List<Tgt3> items1 = list.get(1).getItems();
        Assert.assertEquals(1, items1.size());
        Assert.assertEquals("c", items1.get(0).getName());
    }

    // ======================== 场景 4：多级 join（this→B→C→目标 D） ========================

    @Test
    public void multiLevelJoinResolvesChain() {
        List<Vo4> list = new ArrayList<>(Arrays.asList(vo4(1L)));
        RespConvert.customBindConvert(list, Vo4.class);
        List<D> ds = list.get(0).getDs();
        Assert.assertEquals(1, ds.size());
        Assert.assertEquals("deep", ds.get(0).getV());
    }

    // ======================== 场景 5：无匹配返回空列表 ========================

    @Test
    public void noMatchYieldsEmptyList() {
        List<Vo3> list = new ArrayList<>(Arrays.asList(vo3(999L)));
        RespConvert.customBindConvert(list, Vo3.class);
        Assert.assertNotNull(list.get(0).getItems());
        Assert.assertTrue(list.get(0).getItems().isEmpty());
    }

    // ======================== 场景 6：非法 condition 不抛异常 ========================

    @Test
    public void invalidConditionSkipsWithoutException() {
        List<VoBad> list = new ArrayList<>(Arrays.asList(voBad(1L)));
        RespConvert.customBindConvert(list, VoBad.class);
        Assert.assertNotNull(list.get(0).getItems());
        Assert.assertTrue(list.get(0).getItems().isEmpty());
    }

    // ======================== 场景 7：BindEntity 简单绑定（字段类型即实体类型，装首个完整实体） ========================

    @Test
    public void wholeEntitySimpleBindingFillsEntity() {
        List<Vo5> list = new ArrayList<>(Arrays.asList(vo5(1L)));
        RespConvert.customBindConvert(list, Vo5.class);
        Assert.assertNotNull(list.get(0).getTgt());
        Assert.assertEquals(Long.valueOf(1L), list.get(0).getTgt().getTid());
        Assert.assertEquals("one", list.get(0).getTgt().getName());
    }

    // ======================== 场景 8：BindFieldList 简单绑定（List<基本类型> 装 extractField 值列表） ========================

    @Test
    public void extractValueListSimpleBinding() {
        List<Vo6> list = new ArrayList<>(Arrays.asList(vo6(1L)));
        RespConvert.customBindConvert(list, Vo6.class);
        Assert.assertEquals(Arrays.asList("one"), list.get(0).getNames());
    }

    // ======================== 场景 9：BindFieldList + condition（一对多字段值列表扇出） ========================

    @Test
    public void extractValueListConditionFansOut() {
        List<Vo7> list = new ArrayList<>(Arrays.asList(vo7(1L)));
        RespConvert.customBindConvert(list, Vo7.class);
        // id=1 经 Mid 扇出到 Tgt3 a、b，提取 name 得值列表
        Assert.assertEquals(Arrays.asList("a", "b"), list.get(0).getItemNames());
    }

    // ======================== 场景 10：BindEntity + condition（condition 链装首个完整实体） ========================

    @Test
    public void wholeEntityConditionBinding() {
        List<Vo8> list = new ArrayList<>(Arrays.asList(vo8(1L)));
        RespConvert.customBindConvert(list, Vo8.class);
        Assert.assertNotNull(list.get(0).getTgt());
        Assert.assertEquals(Long.valueOf(1L), list.get(0).getTgt().getTid());
        Assert.assertEquals("one", list.get(0).getTgt().getName());
    }

    // ======================== 测试实体 / VO 与工厂方法 ========================
    // 说明：ReflectionUtil 通过 getter 读值、setter 写值，故显式提供访问器；
    //       工厂方法直接给私有字段赋值（嵌套类私有成员在外部类内可访问）。

    public static class Tgt {
        private Long tid;
        private String name;

        public Long getTid() {
            return tid;
        }

        public String getName() {
            return name;
        }
    }

    public static class Tgt2 {
        private String x;
        private Long y;
        private String label;

        public String getX() {
            return x;
        }

        public Long getY() {
            return y;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class Mid {
        private Long midA;
        private Long midB;

        public Long getMidA() {
            return midA;
        }

        public Long getMidB() {
            return midB;
        }
    }

    public static class Tgt3 {
        private Long tid;
        private String name;

        public Long getTid() {
            return tid;
        }

        public String getName() {
            return name;
        }
    }

    public static class B {
        private Long a;
        private Long b;

        public Long getA() {
            return a;
        }

        public Long getB() {
            return b;
        }
    }

    public static class C {
        private Long c;
        private Long d;

        public Long getC() {
            return c;
        }

        public Long getD() {
            return d;
        }
    }

    public static class D {
        private Long col;
        private String v;

        public Long getCol() {
            return col;
        }

        public String getV() {
            return v;
        }
    }

    public static class Vo1 {
        private Long id;
        @BindRepo(entity = Tgt.class, condition = "this.id = tid", extractField = "name")
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Vo2 {
        private String a;
        private Long b;
        @BindRepo(entity = Tgt2.class, condition = "this.a = x AND this.b = y", extractField = "label")
        private String label;

        public String getA() {
            return a;
        }

        public void setA(String a) {
            this.a = a;
        }

        public Long getB() {
            return b;
        }

        public void setB(Long b) {
            this.b = b;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class Vo3 {
        private Long id;
        @BindRepo(entity = Tgt3.class, condition = "this.id = Mid.midA AND Mid.midB = tid")
        private List<Tgt3> items;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public List<Tgt3> getItems() {
            return items;
        }

        public void setItems(List<Tgt3> items) {
            this.items = items;
        }
    }

    public static class Vo4 {
        private Long x;
        @BindRepo(entity = D.class, condition = "this.x = B.a AND B.b = C.c AND C.d = col")
        private List<D> ds;

        public Long getX() {
            return x;
        }

        public void setX(Long x) {
            this.x = x;
        }

        public List<D> getDs() {
            return ds;
        }

        public void setDs(List<D> ds) {
            this.ds = ds;
        }
    }

    public static class VoBad {
        private Long id;
        @BindRepo(entity = Tgt3.class, condition = "this.id")
        private List<Tgt3> items;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public List<Tgt3> getItems() {
            return items;
        }

        public void setItems(List<Tgt3> items) {
            this.items = items;
        }
    }

    public static class Vo5 {
        private Long src;
        @BindRepo(entity = Tgt.class, matchField = "tid", sourceField = "src")
        private Tgt tgt;

        public Long getSrc() {
            return src;
        }

        public void setSrc(Long src) {
            this.src = src;
        }

        public Tgt getTgt() {
            return tgt;
        }

        public void setTgt(Tgt tgt) {
            this.tgt = tgt;
        }
    }

    public static class Vo6 {
        private Long src;
        @BindRepo(entity = Tgt.class, matchField = "tid", sourceField = "src", extractField = "name")
        private List<String> names;

        public Long getSrc() {
            return src;
        }

        public void setSrc(Long src) {
            this.src = src;
        }

        public List<String> getNames() {
            return names;
        }

        public void setNames(List<String> names) {
            this.names = names;
        }
    }

    public static class Vo7 {
        private Long id;
        @BindRepo(entity = Tgt3.class, condition = "this.id = Mid.midA AND Mid.midB = tid", extractField = "name")
        private List<String> itemNames;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public List<String> getItemNames() {
            return itemNames;
        }

        public void setItemNames(List<String> itemNames) {
            this.itemNames = itemNames;
        }
    }

    public static class Vo8 {
        private Long id;
        @BindRepo(entity = Tgt.class, condition = "this.id = tid")
        private Tgt tgt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Tgt getTgt() {
            return tgt;
        }

        public void setTgt(Tgt tgt) {
            this.tgt = tgt;
        }
    }

    private static Tgt tgt(Long tid, String name) {
        Tgt t = new Tgt();
        t.tid = tid;
        t.name = name;
        return t;
    }

    private static Tgt2 tgt2(String x, Long y, String label) {
        Tgt2 t = new Tgt2();
        t.x = x;
        t.y = y;
        t.label = label;
        return t;
    }

    private static Mid mid(Long midA, Long midB) {
        Mid m = new Mid();
        m.midA = midA;
        m.midB = midB;
        return m;
    }

    private static Tgt3 tgt3(Long tid, String name) {
        Tgt3 t = new Tgt3();
        t.tid = tid;
        t.name = name;
        return t;
    }

    private static B b(Long a, Long b) {
        B o = new B();
        o.a = a;
        o.b = b;
        return o;
    }

    private static C c(Long c, Long d) {
        C o = new C();
        o.c = c;
        o.d = d;
        return o;
    }

    private static D d(Long col, String v) {
        D o = new D();
        o.col = col;
        o.v = v;
        return o;
    }

    private static Vo1 vo1(Long id) {
        Vo1 o = new Vo1();
        o.id = id;
        return o;
    }

    private static Vo2 vo2(String a, Long b) {
        Vo2 o = new Vo2();
        o.a = a;
        o.b = b;
        return o;
    }

    private static Vo3 vo3(Long id) {
        Vo3 o = new Vo3();
        o.id = id;
        return o;
    }

    private static Vo4 vo4(Long x) {
        Vo4 o = new Vo4();
        o.x = x;
        return o;
    }

    private static VoBad voBad(Long id) {
        VoBad o = new VoBad();
        o.id = id;
        return o;
    }

    private static Vo5 vo5(Long src) {
        Vo5 o = new Vo5();
        o.src = src;
        return o;
    }

    private static Vo6 vo6(Long src) {
        Vo6 o = new Vo6();
        o.src = src;
        return o;
    }

    private static Vo7 vo7(Long id) {
        Vo7 o = new Vo7();
        o.id = id;
        return o;
    }

    private static Vo8 vo8(Long id) {
        Vo8 o = new Vo8();
        o.id = id;
        return o;
    }
}

