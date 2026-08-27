package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.meta.DimensionDeriveSupport;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Title: DimensionDeriveTest
 * Description: 实体级维度派生单测（2026-08-24 背景：表模板只存实体，导入/骨架套用后
 * 维度按实体列结论重派生同步刷新——维度跟实体单源走，模板无需冗余存维度）：
 * 派生口径（role=dimension 建维度/Date 列补年份维度）、按表刷新（认证维度保留/他表维度不动）
 *
 * @author Sharp
 * @since 2026/8/24
 */
public class DimensionDeriveTest {

    private EntityDef.EntityFieldDef field(String name, String role, String type) {
        EntityDef.EntityFieldDef f = new EntityDef.EntityFieldDef();
        f.setName(name);
        f.setRole(role);
        f.setType(type);
        f.setDisplayName(name + "_显示名");
        return f;
    }

    private EntityDef entity(String name, String table, EntityDef.EntityFieldDef... fields) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable(table);
        e.setFields(new ArrayList<>(Arrays.asList(fields)));
        return e;
    }

    private DimensionDef dim(String name, String expression, Boolean certified) {
        DimensionDef d = new DimensionDef();
        d.setName(name);
        d.setExpression(expression);
        d.setCertified(certified);
        return d;
    }

    /** 派生口径：维度列建三段式维度，非维度列跳过；Date 列另补 _year 粒度维度（基础维度不带粒度） */
    @Test
    public void deriveFollowsEntityRoles() {
        EntityDef e = entity("ht_info", "dw.ht_info",
                field("region", "dimension", "String"),
                field("create_time", "dimension", "Date"),
                field("amt", "metric", "Decimal"),
                field("remark", "ignore", "String"));
        List<DimensionDef> dims = DimensionDeriveSupport.deriveFromEntities(
                Collections.singletonList(e), new HashSet<>());
        Assert.assertEquals("region + create_time + create_time_year", 3, dims.size());
        Assert.assertEquals("dw.ht_info.region", dims.get(0).getExpression());
        Assert.assertNull("基础维度不带粒度（dy 类列值直用）", dims.get(0).getGranularity());
        DimensionDef year = dims.get(1);
        Assert.assertEquals("create_time_year", year.getName());
        Assert.assertEquals("year", year.getGranularity());
        Assert.assertEquals("time", year.getType());
        Assert.assertEquals("time", dims.get(2).getType());
    }

    /** 多值列派生：字段 multi_value=true 的维度列派生维度带 match=multi（问数过滤改写 FIND_IN_SET）；
     *  背景：business_types 类逗号分隔多值 code 列是合法维度形态，等值过滤需包含匹配 */
    @Test
    public void deriveMultiValueFieldCarriesMatch() {
        EntityDef.EntityFieldDef multi = field("business_types", "dimension", "String");
        multi.setMultiValue(true);
        EntityDef e = entity("ht_info", "dw.ht_info",
                multi, field("region", "dimension", "String"));
        List<DimensionDef> dims = DimensionDeriveSupport.deriveFromEntities(
                Collections.singletonList(e), new HashSet<>());
        Assert.assertEquals(2, dims.size());
        Assert.assertEquals("多值列派生维度带 match=multi", "multi", dims.get(0).getMatch());
        Assert.assertNull("普通维度不带 match", dims.get(1).getMatch());
    }

    /** 按表刷新：目标表旧维度被派生结果替换，认证维度与他表维度保留；
     *  背景：模板导入同步更新 dimensions 草稿，已认证人工维度不被覆盖 */
    @Test
    public void rederiveReplacesScopedKeepsCertifiedAndOthers() {
        EntityDef e = entity("ht_info", "dw.ht_info",
                field("region", "dimension", "String"),
                field("status", "dimension", "String"));
        List<DimensionDef> dims = new ArrayList<>(Arrays.asList(
                dim("region", "dw.ht_info.region", null),          // 目标表未认证：被替换
                dim("region_alias", "dw.ht_info.region", true),    // 目标表已认证：保留
                dim("dept", "dw.ht_org.dept", null)));             // 他表：不动
        DimensionDeriveSupport.rederiveForTables(dims, Collections.singletonList(e),
                Collections.singletonList("dw.ht_info"));
        List<String> names = new ArrayList<>();
        dims.forEach(d -> names.add(d.getName()));
        Assert.assertTrue("认证维度保留", names.contains("region_alias"));
        Assert.assertTrue("他表维度不动", names.contains("dept"));
        Assert.assertTrue("按实体结论派生补入", names.contains("region") && names.contains("status"));
        Assert.assertEquals("旧 region 移除 + 派生 region/status 补入", 4, dims.size());
    }

    /** 命名碰撞：派生名与保留维度（含他表/认证）重名时按表前缀语义去重 */
    @Test
    public void rederiveAvoidsNameCollision() {
        EntityDef e = entity("ht_info", "dw.ht_info", field("region", "dimension", "String"));
        List<DimensionDef> dims = new ArrayList<>(Collections.singletonList(
                dim("region", "dw.ht_org.region", true)));   // 他表认证维度已占名
        DimensionDeriveSupport.rederiveForTables(dims, Collections.singletonList(e),
                Collections.singletonList("dw.ht_info"));
        Assert.assertEquals("ht_info_region", dims.get(1).getName());
    }
}
