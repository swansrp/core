package com.bidr.insight.smartquery;

import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.meta.SkeletonMergeSupport;
import com.bidr.insight.smartquery.meta.TableTemplateSupport;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Title: TableTemplateApplyTest
 * Description: 表级资产模板套用单测（2026-08-24 背景：跨 Agent 同表免重复配置——
 * 已确认实体按 数据源+表 沉淀模板，其他 Agent 骨架构建时自动套用人工结论）：
 * 表级匹配合并、实体名对齐、未确认模板不携入、本 Agent 草稿结论优先于模板
 *
 * @author Sharp
 * @since 2026/8/24
 */
public class TableTemplateApplyTest {

    private EntityDef.EntityFieldDef field(String name) {
        EntityDef.EntityFieldDef f = new EntityDef.EntityFieldDef();
        f.setName(name);
        return f;
    }

    /** 模板：已确认实体（含表级键/分区与人工编辑列结论），实体名与消费侧可能不同 */
    private EntityDef template(String name, String table) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable(table);
        e.setConfirmed(true);
        e.setPrimaryKey(Collections.singletonList("project_code"));
        e.setPartitionColumn("dy");
        EntityDef.EntityFieldDef amt = field("amt");
        amt.setRole("measure");
        amt.setUnit("万元");
        amt.setEdited(true);
        EntityDef.EntityFieldDef dept = field("dept");
        dept.setRole("dimension");
        dept.setDimGroup("组织类");
        dept.setEdited(true);
        e.setFields(new ArrayList<>(Arrays.asList(amt, dept, field("remark"))));
        return e;
    }

    /** 新骨架重建项（未确认，同表），列清单含模板同名列与物理表新增列 */
    private EntityDef rebuilt(String name, String table) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable(table);
        e.setFields(new ArrayList<>(Arrays.asList(field("amt"), field("dept"), field("remark"), field("new_col"))));
        return e;
    }

    /** 主场景：同表模板套用——确认态/键/分区/人工编辑列结论全部携入，物理表新增列不受影响 */
    @Test
    public void templateAppliedByTable() {
        EntityDef ne = rebuilt("ht_info", "dw.ht_info");
        List<EntityDef> rebuilt = new ArrayList<>(Collections.singletonList(ne));
        TableTemplateSupport.applyTemplates(rebuilt, table ->
                "dw.ht_info".equals(table) ? template("ht_info", table) : null);
        Assert.assertTrue("模板确认态应携入", Boolean.TRUE.equals(ne.getConfirmed()));
        Assert.assertEquals(Collections.singletonList("project_code"), ne.getPrimaryKey());
        Assert.assertEquals("dy", ne.getPartitionColumn());
        EntityDef.EntityFieldDef amt = ne.getFields().get(0);
        Assert.assertEquals("measure", amt.getRole());
        Assert.assertEquals("万元", amt.getUnit());
        EntityDef.EntityFieldDef dept = ne.getFields().get(1);
        Assert.assertEquals("组织类", dept.getDimGroup());
        Assert.assertNull("物理表新增列无模板结论", ne.getFields().get(3).getRole());
    }

    /** 模板实体名与重建项不同（唯一化碰撞）也命中：身份是表不是名，套用前按重建项对齐名 */
    @Test
    public void templateNameAlignedToRebuilt() {
        EntityDef ne = rebuilt("ht_info_2", "dw.ht_info");
        List<EntityDef> rebuilt = new ArrayList<>(Collections.singletonList(ne));
        TableTemplateSupport.applyTemplates(rebuilt, table -> template("ht_info", table));
        Assert.assertTrue("名不同仍按表命中", Boolean.TRUE.equals(ne.getConfirmed()));
        Assert.assertEquals("ht_info_2", ne.getName());
    }

    /** 未确认的模板实体不携入（与 carryConfirmedFields 同口径：只认已确认结论） */
    @Test
    public void unconfirmedTemplateIgnored() {
        EntityDef ne = rebuilt("ht_info", "dw.ht_info");
        EntityDef t = template("ht_info", "dw.ht_info");
        t.setConfirmed(null);
        List<EntityDef> rebuilt = new ArrayList<>(Collections.singletonList(ne));
        TableTemplateSupport.applyTemplates(rebuilt, table -> t);
        Assert.assertNull(ne.getConfirmed());
        Assert.assertNull(ne.getPrimaryKey());
    }

    /** 优先级：模板先套用，本 Agent 草稿已确认结论后到（carryConfirmedFromOld 语义）同名列覆盖模板 */
    @Test
    public void ownDraftConfirmedWinsOverTemplate() {
        EntityDef ne = rebuilt("ht_info", "dw.ht_info");
        List<EntityDef> rebuilt = new ArrayList<>(Collections.singletonList(ne));
        TableTemplateSupport.applyTemplates(rebuilt, table -> template("ht_info", table));
        // 模拟本 Agent 旧草稿：已确认且人工改了键
        EntityDef own = template("ht_info", "dw.ht_info");
        own.setPrimaryKey(Collections.singletonList("id"));
        SkeletonMergeSupport.carryConfirmedFields(rebuilt, new EntityDef[]{own});
        Assert.assertEquals("本 Agent 结论覆盖模板", Collections.singletonList("id"), ne.getPrimaryKey());
        Assert.assertEquals("dy", ne.getPartitionColumn());
    }

    /** 显式导入场景（页面「从模板导入」）：已确认实体跳过不被模板覆盖，返回清单只含实际套用表；
     *  背景：2026-08-24 模板沉淀/导入改页面显式动作，防保存自动沉淀污染共享模板 */
    @Test
    public void skipConfirmedOnExplicitImport() {
        EntityDef confirmed = rebuilt("ht_info", "dw.ht_info");
        confirmed.setConfirmed(true);
        confirmed.setPrimaryKey(Collections.singletonList("own_key"));
        EntityDef fresh = rebuilt("ht_pay", "dw.ht_pay");
        List<EntityDef> rebuilt = new ArrayList<>(Arrays.asList(confirmed, fresh));
        List<String> applied = TableTemplateSupport.applyTemplates(rebuilt,
                table -> template("tpl", table), true);
        Assert.assertEquals("已确认实体跳过，仅套用新表", Collections.singletonList("dw.ht_pay"), applied);
        Assert.assertEquals("已确认实体的键不被模板覆盖", Collections.singletonList("own_key"), confirmed.getPrimaryKey());
        Assert.assertTrue("新表套用后确认态携入", Boolean.TRUE.equals(fresh.getConfirmed()));
    }

    /** 沉淀候选筛选：认证本表只同步本表（不把其他已认证表一并推上模板库）；未认证/无表实体不沉淀。
     *  背景：2026-08-25 用户反馈「认证本表同步的是 6 张表」，沉淀接口加表清单限定 */
    @Test
    public void sedimentScopedToCertifiedTableOnly() {
        EntityDef a = template("ht_info", "dw.ht_info");
        a.setCertified(true);
        EntityDef b = template("ht_pay", "dw.ht_pay");
        b.setCertified(true);
        EntityDef unauth = template("ht_bid", "dw.ht_bid");
        List<EntityDef> all = Arrays.asList(a, b, unauth);

        // 无清单：全部已认证表入候选（「保存到模板」按钮语义）
        List<EntityDef> whole = TableTemplateSupport.selectSedimentTargets(all, null);
        Assert.assertEquals("无清单沉淀全部已认证表", 2, whole.size());

        // 限定本表：仅当前认证表入候选，其余已认证表与未认证表均不进（认证本表语义）
        List<EntityDef> scoped = TableTemplateSupport.selectSedimentTargets(
                all, new HashSet<>(Collections.singletonList("dw.ht_info")));
        Assert.assertEquals("清单限定仅沉淀本表", 1, scoped.size());
        Assert.assertEquals("dw.ht_info", scoped.get(0).getTable());
    }
}
