package com.bidr.kernel.controller.inf.statistic;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bidr.kernel.mybatis.repository.BaseSqlRepo;
import com.bidr.kernel.vo.portal.ConditionVO;
import com.bidr.kernel.vo.portal.Query;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import com.github.yulichang.wrapper.segments.SelectString;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;

/**
 * Title: AdminStatisticNoPortalSubFromTest
 * Description: 未挂 PortalCommonService 的通用 admin 控制器(如 AdminConfigController)统计接口子查询补列单测。
 * <p>
 * 背景：这类控制器的 buildSubFromWrapper 分支原先不补任何 SELECT 列，子查询生成 `SELECT FROM 表` 直接语法错误；
 * 且外层统计 SQL(metricColumn 分组 / metricCondition case-when / statisticColumn 求和)按前端传来的
 * 属性名口径引用字段，所以子查询必须把每个实体列别名为属性名(config_group AS configGroup)。
 * <p>
 * 纯单测：mock 接口默认实现 + 真实 TableInfo + 真实 parseQuery，不连数据库。
 *
 * @author Sharp
 * @since 2026/11/20
 */
public class AdminStatisticNoPortalSubFromTest {

    /**
     * 覆盖字段映射四种形态：@TableId 显式列名 / @TableField 显式列名 / 无注解驼峰回退 / @TableField(exist=false) 剔除
     */
    public static class Fixture {
        @TableId(value = "fixture_id", type = IdType.AUTO)
        private Long fixtureId;

        @TableField("fixture_name")
        private String fixtureName;

        private String fixtureGroup;

        @TableField(exist = false)
        private String transientField;
    }

    private static AdminStatisticBaseInf<Fixture, Object> inf;

    @BeforeClass
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void setUp() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(AdminStatisticNoPortalSubFromTest.class.getName());
        TableInfo tableInfo = TableInfoHelper.initTableInfo(assistant, Fixture.class);
        Assert.assertNotNull(tableInfo);

        BaseSqlRepo repo = Mockito.mock(BaseSqlRepo.class, Mockito.withSettings()
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));

        inf = (AdminStatisticBaseInf<Fixture, Object>) Mockito.mock(AdminStatisticBaseInf.class,
                Mockito.withSettings().defaultAnswer(inv -> {
                    switch (inv.getMethod().getName()) {
                        case "getEntityClass":
                            return Fixture.class;
                        case "getPortalService":
                            return null;
                        case "getRepo":
                            return repo;
                        default:
                            return Mockito.CALLS_REAL_METHODS.answer(inv);
                    }
                }));
    }

    /**
     * 子查询必须把实体列(含主键)别名为属性名；exist=false 字段不得出现；不允许裸列名
     */
    @Test
    public void subQueryAliasesEveryEntityColumnAsProperty() {
        MPJLambdaWrapper<Fixture> from = new MPJLambdaWrapper<>(Fixture.class);
        inf.buildSubSelectColumns(from);
        String sqlSelect = from.getSqlSelect();

        Assert.assertTrue(sqlSelect.contains("fixture_id AS fixtureId"));
        Assert.assertTrue(sqlSelect.contains("fixture_name AS fixtureName"));
        Assert.assertTrue(sqlSelect.contains("fixture_group AS fixtureGroup"));
        Assert.assertFalse(sqlSelect.contains("transient_field"));
        Assert.assertFalse(sqlSelect.contains("transientField"));
        for (String segment : sqlSelect.split(",")) {
            Assert.assertTrue("列必须带 AS 属性名别名: " + segment, segment.trim().toUpperCase().contains(" AS "));
        }
    }

    /**
     * 还原线上报障请求形状(count 排行榜，外层按属性名分组)：子查询必须暴露同名别名；
     * 同时查询条件(属性名口径)须经 getColumnName 解析成 DB 列后构建进子查询 WHERE
     */
    @Test
    public void rankingRequestShapeStaysResolvable() {
        ConditionVO condition = new ConditionVO();
        condition.setProperty("fixtureGroup");
        condition.setRelation(1);
        condition.setValue(Collections.singletonList("A"));
        Query query = new Query();
        query.setConditionList(Collections.singletonList(condition));

        MPJLambdaWrapper<Fixture> outer = new MPJLambdaWrapper<>(Fixture.class);
        outer.getSelectColum().add(new SelectString("count(1) as statistic", outer.getAlias()));
        outer.getSelectColum().add(new SelectString(
                String.format("IFNULL(%s, '%s') as %s", "fixtureGroup", "NULL", "fixtureGroup"), outer.getAlias()));
        outer.groupBy("fixtureGroup");
        outer.orderByDesc("statistic");

        final MPJLambdaWrapper<Fixture>[] captured = new MPJLambdaWrapper[1];
        outer.from(from -> {
            captured[0] = from;
            return inf.buildSubFromWrapper(query, from);
        });

        String fromSelect = captured[0].getSqlSelect();
        Assert.assertTrue("子查询须暴露分组属性名别名", fromSelect.contains("fixture_group AS fixtureGroup"));

        String fromSegment = captured[0].getSqlSegment();
        Assert.assertTrue("查询条件须解析成 DB 列并构建进子查询", fromSegment.contains("fixture_group"));

        String outerSelect = outer.getSqlSelect();
        Assert.assertTrue("外层统计表达式按属性名口径不变", outerSelect.contains("IFNULL(fixtureGroup"));
    }
}
