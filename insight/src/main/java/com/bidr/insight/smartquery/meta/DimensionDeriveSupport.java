package com.bidr.insight.smartquery.meta;

import com.bidr.insight.smartquery.layer.DimensionDef;
import com.bidr.insight.smartquery.layer.EntityDef;
import com.bidr.kernel.utils.FuncUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Title: DimensionDeriveSupport
 * Description: 实体级维度派生：从实体字段（role=dimension）重建维度清单，
 * 与 SkeletonBuilder 第二遍同口径（Date 列补年份粒度维度、同名按表前缀去重）。
 * 消费场景：表模板导入——模板只存实体，套用后维度按实体结论重派生（维度跟实体单源走，
 * 模板无需冗余存维度）；骨架链路套用模板的表同样重派生，保证人工列结论与维度一致。
 * 刷新语义：dims 中表达式属于目标表的未认证维度先移除再补派生，已认证维度保留不被覆盖
 *
 * @author Sharp
 * @since 2026/8/24
 */
@Slf4j
public class DimensionDeriveSupport {

    private DimensionDeriveSupport() {
    }

    /** 从实体派生维度（静态可测）：每实体取 role=dimension 的列建维度（三段式表达式），
     *  Date 列另补年份粒度维度（「每年/按年看」高频形态，口径同 SkeletonBuilder）；
     *  维度名冲突按 表前缀 去重（used 为跨实体共享命名空间，调用方传入已有维度名防碰撞） */
    public static List<DimensionDef> deriveFromEntities(List<EntityDef> entities, Set<String> usedNames) {
        List<DimensionDef> out = new ArrayList<>();
        if (FuncUtil.isEmpty(entities)) {
            return out;
        }
        Set<String> used = usedNames != null ? usedNames : new HashSet<>();
        for (EntityDef e : entities) {
            if (FuncUtil.isEmpty(e.getTable()) || FuncUtil.isEmpty(e.getFields())) {
                continue;
            }
            String tbl = shortTable(e.getTable());
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (!"dimension".equals(f.getRole()) || FuncUtil.isEmpty(f.getName())) {
                    continue;
                }
                // 禁用列不派生维度（问数侧不可见；重新启用后随下次重派生自然回归）
                if (Boolean.TRUE.equals(f.getDisabled())) {
                    continue;
                }
                DimensionDef dim = new DimensionDef();
                dim.setName(ColumnConventions.uniqueDimName(f.getName(), tbl, used));
                dim.setDisplayName(FuncUtil.isNotEmpty(f.getDisplayName()) ? f.getDisplayName() : f.getName());
                dim.setExpression(e.getTable() + "." + f.getName());
                // 多值列（逗号分隔 code 串）：维度带 match=multi，问数等值过滤改写 FIND_IN_SET
                if (Boolean.TRUE.equals(f.getMultiValue())) {
                    dim.setMatch("multi");
                }
                // 基础维度不带粒度（口径同 SkeletonBuilder）：dy 类列值直用不包函数，
                // 年份轴由 _year 派生维度单独承载；字段级粒度留在实体供提示词消费
                if ("Date".equals(f.getType())) {
                    dim.setType("time");
                    // 年份粒度维度：名称加 _year，expression 仍直引日期列，SQL 生成器按粒度包 YEAR()
                    DimensionDef year = new DimensionDef();
                    year.setName(ColumnConventions.uniqueDimName(f.getName() + "_year", tbl, used));
                    year.setDisplayName(dim.getDisplayName() + "（年）");
                    year.setExpression(dim.getExpression());
                    year.setType("time");
                    year.setGranularity("year");
                    out.add(year);
                }
                out.add(dim);
            }
        }
        return out;
    }

    /** 按表重派生刷新（静态可测）：移除 dims 中表达式属于 tables 的未认证维度（认证维度保留），
     *  再按这些表对应实体派生补入；命名空间 = 保留项名称（含认证的本表维度，防重名碰撞） */
    public static void rederiveForTables(List<DimensionDef> dims, List<EntityDef> entities, List<String> tables) {
        if (dims == null || FuncUtil.isEmpty(tables) || FuncUtil.isEmpty(entities)) {
            return;
        }
        Set<String> scoped = new HashSet<>(tables);
        Set<String> used = new HashSet<>();
        Iterator<DimensionDef> it = dims.iterator();
        while (it.hasNext()) {
            DimensionDef d = it.next();
            String tbl = tableOf(d.getExpression());
            if (tbl != null && scoped.contains(tbl) && !Boolean.TRUE.equals(d.getCertified())) {
                it.remove();
                continue;
            }
            // 保留项全部计入命名空间（含认证的本表维度，防派生重名碰撞）
            if (FuncUtil.isNotEmpty(d.getName())) {
                used.add(d.getName());
            }
        }
        List<EntityDef> target = new ArrayList<>();
        for (EntityDef e : entities) {
            if (e.getTable() != null && scoped.contains(e.getTable())) {
                target.add(e);
            }
        }
        dims.addAll(deriveFromEntities(target, used));
    }

    /** 禁用列维度清理（静态可测）：实体列已禁用时移除由该列派生的未认证维度（认证维度保留属人工结论）；
     *  骨架链在 carryConfirmedFields 携入 disabled 后调用，保证禁用列问数侧不可见 */
    public static void dropDisabledColumnDims(List<DimensionDef> dims, List<EntityDef> entities) {
        if (dims == null || FuncUtil.isEmpty(entities)) {
            return;
        }
        Set<String> disabledCols = new HashSet<>();
        for (EntityDef e : entities) {
            if (FuncUtil.isEmpty(e.getTable()) || FuncUtil.isEmpty(e.getFields())) {
                continue;
            }
            for (EntityDef.EntityFieldDef f : e.getFields()) {
                if (Boolean.TRUE.equals(f.getDisabled()) && FuncUtil.isNotEmpty(f.getName())) {
                    disabledCols.add(e.getTable() + "." + f.getName());
                }
            }
        }
        if (disabledCols.isEmpty()) {
            return;
        }
        dims.removeIf(d -> !Boolean.TRUE.equals(d.getCertified())
                && d.getExpression() != null && disabledCols.contains(d.getExpression()));
    }

    /** 表达式三段式取表全名（db.tbl.col → db.tbl）；非三段式返回 null */
    public static String tableOf(String expression) {
        if (FuncUtil.isEmpty(expression)) {
            return null;
        }
        int last = expression.lastIndexOf('.');
        return last > 0 ? expression.substring(0, last) : null;
    }

    /** 表全名取短名（db.tbl → tbl）：维度名冲突时表前缀语义命名用 */
    private static String shortTable(String full) {
        int i = full.lastIndexOf('.');
        return i >= 0 ? full.substring(i + 1) : full;
    }
}
