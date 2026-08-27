package com.bidr.insight.smartquery.service;

import com.bidr.insight.smartquery.constant.dict.TechColumnDict;
import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.insight.smartquery.layer.ValueDomainDef;
import com.bidr.insight.smartquery.meta.ColumnConventions;
import com.bidr.insight.smartquery.meta.CommentValueParser;
import com.bidr.insight.smartquery.meta.ConceptsSupport;
import com.bidr.insight.smartquery.meta.SkeletonMergeSupport;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Title: SmartAgentMetaServiceTest
 * Description: 列角色预选纯函数回归——数仓时间分区列约定（dy=年快照/dm=月快照/dd=日快照）
 * 粒度钉死口径。背景：2026-08-23 用户实证 dy（Int/String 年值列）此前落 ignore 且粒度
 * 选择被 Date 类型门槛锁死，人工确认页无处安放"年份"语义；此测试锁
 * timePartGranularity 契约（含 _dy 后缀形态与非时间分区列不误伤）不回退
 *
 * @author Sharp
 * @since 2026/8/23
 */
public class SmartAgentMetaServiceTest {

    /** 时间分区列：列名本身或 _后缀形态钉死对应粒度 */
    @Test
    public void timePartGranularityRecognizesPartitionConvention() {
        Assert.assertEquals("year", ColumnConventions.timePartGranularity("dy"));
        Assert.assertEquals("year", ColumnConventions.timePartGranularity("DY"));
        Assert.assertEquals("month", ColumnConventions.timePartGranularity("dm"));
        Assert.assertEquals("day", ColumnConventions.timePartGranularity("dd"));
        Assert.assertEquals("year", ColumnConventions.timePartGranularity("snap_dy"));
    }

    /** 非时间分区列不误伤：普通业务列/空值一律 null（走原启发式预选） */
    @Test
    public void timePartGranularityIgnoresOtherColumns() {
        Assert.assertNull(ColumnConventions.timePartGranularity(null));
        Assert.assertNull(ColumnConventions.timePartGranularity("project_id"));
        Assert.assertNull(ColumnConventions.timePartGranularity("body_type"));
        Assert.assertNull(ColumnConventions.timePartGranularity("day"));
        Assert.assertNull(ColumnConventions.timePartGranularity("today"));
    }

    /** 字段构造助手 */
    private static EntityDef.EntityFieldDef field(String name, String role, String unit,
                                                  String granularity, boolean edited) {
        EntityDef.EntityFieldDef f = new EntityDef.EntityFieldDef();
        f.setName(name);
        f.setRole(role);
        f.setUnit(unit);
        f.setGranularity(granularity);
        f.setEdited(edited ? Boolean.TRUE : null);
        return f;
    }

    private static EntityDef entity(String name, List<EntityDef.EntityFieldDef> fields) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable("db1." + name);
        e.setFields(new ArrayList<>(fields));
        return e;
    }

    /** 字段级合并：已确认表的人工结论不被重建覆盖，未编辑列随物理表刷新（新增列入/删列不残留）。
     *  背景：2026-08-23 用户问「确认的字段覆盖吗」；复核发现 mergeByName 曾把 confirmed 纳入
     *  整体保留口径，导致字段级合并永远不生效（新列也进不来），修正为认证整体保留/确认字段级合并 */
    @Test
    public void carryConfirmedFieldsProtectsManualConclusions() {
        // 旧稿：已确认表（人工键/分区），amt 人工改为度量+元，status 未编辑但挂了值域与归类，dropped_col 物理表已删
        EntityDef old = entity("ht_contract", Arrays.asList(
                field("amt", "metric", "元", null, true),
                field("status", "dimension", null, null, false),
                field("dropped_col", "ignore", null, null, false)));
        old.setConfirmed(true);
        old.setPrimaryKey(Collections.singletonList("biz_key"));
        old.setPartitionColumn("dy");
        old.getFields().get(1).setValueDomain("dom_status");
        old.getFields().get(1).setDimGroup("合同属性");
        // 重建：启发式把 amt 翻案成忽略，物理表新增 new_col，dropped_col 已不在
        EntityDef rebuilt = entity("ht_contract", Arrays.asList(
                field("amt", "ignore", null, null, false),
                field("status", "dimension", null, null, false),
                field("new_col", "ignore", null, null, false)));

        SkeletonMergeSupport.carryConfirmedFields(
                new ArrayList<>(Collections.singletonList(rebuilt)), new EntityDef[]{old});

        // 表级：确认态/键/分区沿用旧值
        Assert.assertEquals(Boolean.TRUE, rebuilt.getConfirmed());
        Assert.assertEquals(Collections.singletonList("biz_key"), rebuilt.getPrimaryKey());
        Assert.assertEquals("dy", rebuilt.getPartitionColumn());
        // 人工编辑列：启发式翻案无效，旧结论沿用且 edited 保留
        EntityDef.EntityFieldDef amt = rebuilt.getFields().get(0);
        Assert.assertEquals("metric", amt.getRole());
        Assert.assertEquals("元", amt.getUnit());
        Assert.assertEquals(Boolean.TRUE, amt.getEdited());
        // 未编辑列：取新骨架值，但旧值域与归类保留；新增列进入，删列不残留
        EntityDef.EntityFieldDef status = rebuilt.getFields().get(1);
        Assert.assertEquals("dom_status", status.getValueDomain());
        Assert.assertEquals("合同属性", status.getDimGroup());
        Assert.assertEquals("new_col", rebuilt.getFields().get(2).getName());
        Assert.assertEquals(3, rebuilt.getFields().size());
    }

    /** 人工修正显示名携入：骨架重建不得回落列备注（维度显示名派生单源），空值不携入（清空回默认）。
     *  背景：2026-08-25 用户反馈实体确认页显示名不能改，实体列显示名改可编辑后补字段级合并保护 */
    @Test
    public void carryConfirmedFieldsCarriesEditedDisplayName() {
        EntityDef.EntityFieldDef oldF = field("lead_org", "dimension", null, null, true);
        oldF.setDisplayName("牵头单位（人工修正名）");
        EntityDef old = entity("ht_contract", Collections.singletonList(oldF));
        old.setConfirmed(true);
        // 重建：启发式按列备注生成新显示名（若携入失效会被覆盖回去）
        EntityDef.EntityFieldDef newF = field("lead_org", "dimension", null, null, false);
        newF.setDisplayName("LEAD_ORG（列备注）");
        EntityDef rebuilt = entity("ht_contract", Collections.singletonList(newF));

        SkeletonMergeSupport.carryConfirmedFields(
                new ArrayList<>(Collections.singletonList(rebuilt)), new EntityDef[]{old});

        Assert.assertEquals("人工修正显示名沿用旧稿", "牵头单位（人工修正名）", newF.getDisplayName());
        Assert.assertEquals(Boolean.TRUE, newF.getEdited());

        // 空显示名不携入：重建保留骨架默认值（清空回默认的语义）
        EntityDef.EntityFieldDef blankF = field("status", "dimension", null, null, true);
        EntityDef oldBlank = entity("ht_payment", Collections.singletonList(blankF));
        oldBlank.setConfirmed(true);
        EntityDef.EntityFieldDef rebuiltF = field("status", "dimension", null, null, false);
        rebuiltF.setDisplayName("状态（列备注）");
        EntityDef rebuiltBlank = entity("ht_payment", Collections.singletonList(rebuiltF));
        SkeletonMergeSupport.carryConfirmedFields(
                new ArrayList<>(Collections.singletonList(rebuiltBlank)), new EntityDef[]{oldBlank});
        Assert.assertEquals("空值不携入，重建默认值保留", "状态（列备注）", rebuiltF.getDisplayName());
    }

    /** 未确认实体不携入：重建全量覆盖（确认前的临时修改不保护，这正是鼓励尽早确认的语义） */
    @Test
    public void carryConfirmedFieldsSkipsUnconfirmed() {
        EntityDef old = entity("ht_payment", Collections.singletonList(
                field("amt", "metric", "元", null, true)));
        EntityDef rebuilt = entity("ht_payment", Collections.singletonList(
                field("amt", "ignore", null, null, false)));

        SkeletonMergeSupport.carryConfirmedFields(
                new ArrayList<>(Collections.singletonList(rebuilt)), new EntityDef[]{old});

        Assert.assertNull(rebuilt.getConfirmed());
        Assert.assertEquals("ignore", rebuilt.getFields().get(0).getRole());
        Assert.assertNull(rebuilt.getFields().get(0).getUnit());
    }

    /** 维度构造助手 */
    private static DimensionDef dim(String name, String disp, String col) {
        DimensionDef d = new DimensionDef();
        d.setName(name);
        d.setDisplayName(disp);
        d.setExpression("db1.t." + col);
        return d;
    }

    /** 启发式目录字典匹配：三路命中口径（词根/中文词/列段）+ 枚举声明顺序优先级 + 未命中落其他桶。
     *  背景：2026-08-23 启发式关键词从代码静态字典升级为系统字典枚举 DimensionGroupDict，此测试锁行为不回退 */
    @Test
    public void matchHierarchyGroupByDict() {
        // 维度名词根命中 → 时间类；列段精确名命中（dy 年快照）→ 时间类
        Assert.assertEquals("时间类", ConceptsSupport.matchHierarchyGroup(dim("sign_date_year", "签订年", "sign_date")));
        Assert.assertEquals("时间类", ConceptsSupport.matchHierarchyGroup(dim("dy", "统计年", "dy")));
        // 显示名中文词命中 → 组织类；同时命中两组 → 枚举顺序优先（时间类在前）
        Assert.assertEquals("组织类", ConceptsSupport.matchHierarchyGroup(dim("lead", "牵头部门", "lead")));
        Assert.assertEquals("时间类", ConceptsSupport.matchHierarchyGroup(dim("org_month", "部门月", "org_month")));
        // 扩充组覆盖：领域组优先于泛切面组（类型/状态置后），"项目状态"落项目类而非状态类；
        // "合同类型"落合同类而非类型类（口径同 GROUP_PRESETS 目录预期）
        Assert.assertEquals("项目类", ConceptsSupport.matchHierarchyGroup(dim("proj_status", "项目状态", "proj_status")));
        Assert.assertEquals("合同类", ConceptsSupport.matchHierarchyGroup(dim("contract_type", "合同类型", "contract_type")));
        Assert.assertEquals("地区类", ConceptsSupport.matchHierarchyGroup(dim("region", "所在地区", "region")));
        Assert.assertEquals("人员类", ConceptsSupport.matchHierarchyGroup(dim("emp_id", "责任人", "emp_id")));
        Assert.assertEquals("状态类", ConceptsSupport.matchHierarchyGroup(dim("audit_state", "审核状态", "audit_state")));
        // 全部未命中 → null（目录工具落「其他」桶；注：旧 fixture "合同状态"在扩组后命中合同类，换真正无匹配项）
        Assert.assertNull(ConceptsSupport.matchHierarchyGroup(dim("remark", "备注", "remark")));
    }

    /** 归类 colExact 数仓实证项（2026-08-23 全量扫仓 392 表的高频列名钉组，比词根稳）：
     *  pmp=生产项目、dct=生产任务、tpc/tcpc=传统采购合同、bprov=归属省份、clue=线索，含科目类新组 */
    @Test
    public void matchHierarchyGroupByWarehouseColExact() {
        Assert.assertEquals("项目类", ConceptsSupport.matchHierarchyGroup(dim("pmp_code", "", "pmp_code")));
        Assert.assertEquals("项目类", ConceptsSupport.matchHierarchyGroup(dim("dct_code", "", "dct_code")));
        Assert.assertEquals("组织类", ConceptsSupport.matchHierarchyGroup(dim("cost_dept_code", "", "cost_dept_code")));
        Assert.assertEquals("地区类", ConceptsSupport.matchHierarchyGroup(dim("bprov_code", "", "bprov_code")));
        Assert.assertEquals("合同类", ConceptsSupport.matchHierarchyGroup(dim("tcpc_id", "", "tcpc_id")));
        Assert.assertEquals("客户类", ConceptsSupport.matchHierarchyGroup(dim("clue_no", "", "clue_no")));
        Assert.assertEquals("人员类", ConceptsSupport.matchHierarchyGroup(dim("user_no", "", "user_no")));
        Assert.assertEquals("科目类", ConceptsSupport.matchHierarchyGroup(dim("account_name", "", "account_name")));
    }

    /** hierarchy 单源派生：背景 2026-08-24 归类单源化改造——dim_group 存实体字段为唯一真源，
     *  hierarchy 完全由实体抽出。派生口径：同表达式多维度（日期列与 _year 派生维度）同组，
     *  未归类维度不入目录（问数目录落「其他」桶），组序随维度草稿出现序 */
    @Test
    public void deriveHierarchyFromEntityDimGroups() {
        // 实体：lead/dept 归组织类，sign_date 归时间类，remark 未归类；amt 是度量不计入
        EntityDef.EntityFieldDef lead = field("lead", "dimension", null, null, false);
        lead.setDimGroup("组织类");
        EntityDef.EntityFieldDef dept = field("dept", "dimension", null, null, false);
        dept.setDimGroup("组织类");
        EntityDef.EntityFieldDef signDate = field("sign_date", "dimension", null, null, false);
        signDate.setDimGroup("时间类");
        EntityDef e = entity("ht_contract", Arrays.asList(
                lead, dept, signDate,
                field("remark", "dimension", null, null, false),
                field("amt", "metric", "元", null, false)));
        // 维度草稿：出现序 组织→时间，日期列同表达式两个维度（基础 + _year 派生）
        List<DimensionDef> dims = Arrays.asList(
                dimOn("db1.ht_contract", "lead", "lead", null),
                dimOn("db1.ht_contract", "dept", "dept", null),
                dimOn("db1.ht_contract", "sign_date", "sign_date", null),
                dimOn("db1.ht_contract", "sign_date_year", "sign_date", "year"),
                dimOn("db1.ht_contract", "remark", "remark", null));

        List<java.util.Map<String, Object>> hierarchy =
                ConceptsSupport.deriveHierarchy(dims, ConceptsSupport.exprGroupMap(
                        Collections.singletonList(e)));

        Assert.assertEquals("两个分组（未归类不入目录）", 2, hierarchy.size());
        Assert.assertEquals("组织类", hierarchy.get(0).get("name"));
        Assert.assertEquals(Arrays.asList("lead", "dept"), hierarchy.get(0).get("members"));
        Assert.assertEquals("时间类", hierarchy.get(1).get("name"));
        Assert.assertEquals("_year 派生维度与基础维度同表达式同组",
                Arrays.asList("sign_date", "sign_date_year"), hierarchy.get(1).get("members"));
    }

    /** 归类真源提取口径：仅 role=dimension 且归类非空的列入；表列小写对齐 */
    @Test
    public void exprGroupMapOnlyCountsDimensionRoleWithGroup() {
        EntityDef.EntityFieldDef d = field("dept", "dimension", null, null, false);
        d.setDimGroup("组织类");
        EntityDef e = entity("HT_Contract", Arrays.asList(
                d,
                field("amt", "metric", "元", null, false),
                field("lead", "dimension", null, null, false)));   // 未归类不计入
        java.util.Map<String, String> map = ConceptsSupport.exprGroupMap(Collections.singletonList(e));
        Assert.assertEquals(1, map.size());
        Assert.assertEquals("组织类", map.get("db1.ht_contract.dept"));
    }

    /** 存量目录回填：只填空且未编辑的维度列；人工编辑过的列与已有归类的列不动。
     *  背景：2026-08-24 单源化迁移——老数据分组存在 hierarchy 而实体字段为空，回填后单源一致 */
    @Test
    public void backfillDimGroupsFillsOnlyEmptyUnedited() throws Exception {
        EntityDef.EntityFieldDef empty = field("lead", "dimension", null, null, false);
        EntityDef.EntityFieldDef edited = field("dept", "dimension", null, null, true);
        EntityDef.EntityFieldDef filled = field("sign_date", "dimension", null, null, false);
        filled.setDimGroup("时间类");
        EntityDef e = entity("ht_contract", Arrays.asList(empty, edited, filled));
        List<DimensionDef> dims = Arrays.asList(
                dimOn("db1.ht_contract", "lead", "lead", null),
                dimOn("db1.ht_contract", "dept", "dept", null),
                dimOn("db1.ht_contract", "sign_date", "sign_date", null));
        com.fasterxml.jackson.databind.JsonNode hierarchy = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree("[{\"name\":\"组织类\",\"members\":[\"lead\",\"dept\"]},"
                        + "{\"name\":\"自定义组\",\"members\":[\"sign_date\"]}]");

        ConceptsSupport.backfillDimGroupsFromHierarchy(Collections.singletonList(e), dims,
                ConceptsSupport.dimGroupOfHierarchy(hierarchy));

        Assert.assertEquals("空归类列回填目录组名", "组织类", empty.getDimGroup());
        Assert.assertNull("人工编辑过的列不回填", edited.getDimGroup());
        Assert.assertEquals("已有归类的列不覆盖（目录里的旧分组不生效）", "时间类", filled.getDimGroup());
    }

    /** 指定表达式构造维度（单源派生用例用：表达式需与实体 表.列 对齐） */
    private static DimensionDef dimOn(String table, String name, String col, String granularity) {
        DimensionDef d = new DimensionDef();
        d.setName(name);
        d.setDisplayName(name);
        d.setExpression(table + "." + col);
        d.setGranularity(granularity);
        return d;
    }

    /** 技术列黑名单（数仓实证）：高频技术列命中精确/前缀模式，业务列不误伤；
     *  特别是时间分区列 dy/dm/dd 与业务编码绝不能被拉黑。
     *  背景：2026-08-23 基于数仓全量扫仓列名统计建 TechColumnDict，骨架预填直接落 ignore */
    @Test
    public void techColumnBlacklistHitsTechColsOnly() {
        // 高频技术列（精确命中）
        for (String col : Arrays.asList("dw_cdt", "pt_key", "dr", "valid_status", "enablestate",
                "masterid", "sequ", "revision", "reghumid", "regdate", "createtime", "modifytime", "creatorid")) {
            Assert.assertTrue(col, TechColumnDict.isTechColumn(col));
        }
        // 前缀族：pk_* 内部主键、def* 自定义扩展字段；大小写不敏感
        for (String col : Arrays.asList("pk_org", "pk_group", "pk_project", "def1", "defitem3", "DW_CDT")) {
            Assert.assertTrue(col, TechColumnDict.isTechColumn(col));
        }
        // 业务列不误伤（时间分区列/业务编码/名称列均不得拉黑）
        for (String col : Arrays.asList("dy", "dm", "dd", "id", "name", "code", "status",
                "pmp_code", "contract_code", "dept_code", "user_no", "create_date", "projectcode")) {
            Assert.assertFalse(col, TechColumnDict.isTechColumn(col));
        }
        Assert.assertFalse(TechColumnDict.isTechColumn(null));
        Assert.assertFalse(TechColumnDict.isTechColumn(""));
    }

    /** 快照类型识别（数仓表名后缀约定）：治理层七种后缀命中自带用法标签，年份表/无约定表不误伤。
     *  背景：2026-08-23 全量扫仓实证后缀语义，注入提示词防 LLM 算错累计数 */
    @Test
    public void snapshotTypeOfRecognizesSuffixConvention() {
        Assert.assertEquals("年全量，直接取目标期", ColumnConventions.snapshotTypeOf("ads_pm_dct_dyf"));
        Assert.assertEquals("年增量，跨年需累加", ColumnConventions.snapshotTypeOf("dws_om_contract_dyi"));
        Assert.assertEquals("年增月粒度，跨月跨年需累加", ColumnConventions.snapshotTypeOf("dws_pm_tcpc_detail_dyidm"));
        Assert.assertEquals("年快照月粒度，取目标月分区", ColumnConventions.snapshotTypeOf("dws_dc_dcp_org_level_dysdm"));
        Assert.assertEquals("月全量，直接取目标月", ColumnConventions.snapshotTypeOf("dim_pub_dept_dmf"));
        Assert.assertEquals("月增量，跨月需累加", ColumnConventions.snapshotTypeOf("dws_dc_dcp_org_level_dmi"));
        Assert.assertEquals("无时间粒度", ColumnConventions.snapshotTypeOf("dim_om_area_no"));
        Assert.assertEquals("年全量，直接取目标期", ColumnConventions.snapshotTypeOf("DIM_PM_PROJECT_DYF"));
        // 年份表/无约定后缀/无下划线/空 → null（不注入快照行）
        Assert.assertNull(ColumnConventions.snapshotTypeOf("dws_com_portrait_2024"));
        Assert.assertNull(ColumnConventions.snapshotTypeOf("erp_cf_main"));
        Assert.assertNull(ColumnConventions.snapshotTypeOf("dim_hr_cube01"));
        Assert.assertNull(ColumnConventions.snapshotTypeOf(null));
        Assert.assertNull(ColumnConventions.snapshotTypeOf(""));
    }

    /** 备注哨兵值解析（-1代表无/外部人员）：单值也出域供"无 X"过滤；普通单对备注仍拒绝；
     *  与常规枚举共存时并入。背景：2026-08-23 数仓实证 36 处负数码缺省语义 */
    @Test
    public void parseCommentCodeValuesExtractsSentinel() {
        // 单哨兵值：单值也收录（旧口径会因不足两对丢弃）
        List<ValueDomainDef.DomainValue> onlySentinel = CommentValueParser.parseCommentCodeVals("合同编码，-1代表无");
        Assert.assertEquals(1, onlySentinel.size());
        Assert.assertEquals("-1", onlySentinel.get(0).getCode());
        Assert.assertEquals("无", onlySentinel.get(0).getLabel());
        // 哨兵与常规枚举共存：并入同一域（标签不为负数码的中文语义）
        List<ValueDomainDef.DomainValue> mixed = CommentValueParser.parseCommentCodeVals(
                "付款计划状态，0生效、1变更中、2废弃，-1代表无计划");
        Assert.assertEquals(4, mixed.size());
        // 普通单对备注仍拒绝（防垃圾域）；无码值语义备注空出
        Assert.assertTrue(CommentValueParser.parseCommentCodeVals("合同编码").isEmpty());
        Assert.assertTrue(CommentValueParser.parseCommentCodeVals("启用状态,1=未启用").isEmpty());
    }
}
