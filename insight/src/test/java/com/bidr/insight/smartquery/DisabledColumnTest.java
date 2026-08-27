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
 * Title: DisabledColumnTest
 * Description: 实体禁用列（实体确认页「禁用」勾选）回归用例（2026-08-25 用户需求：
 * disable 的列不参与 LLM 输入）：LLM 输入切片剔除、维度派生跳过、骨架链维度清理
 *
 * @author Sharp
 * @since 2026/8/25
 */
public class DisabledColumnTest {

    private static EntityDef.EntityFieldDef field(String name, String role, boolean disabled) {
        EntityDef.EntityFieldDef f = new EntityDef.EntityFieldDef();
        f.setName(name);
        f.setRole(role);
        f.setDisabled(disabled ? Boolean.TRUE : null);
        return f;
    }

    private static EntityDef entity(String name, EntityDef.EntityFieldDef... fields) {
        EntityDef e = new EntityDef();
        e.setName(name);
        e.setTable("dw." + name);
        e.setFields(new ArrayList<>(Arrays.asList(fields)));
        return e;
    }

    private static DimensionDef dim(String name, String expression, boolean certified) {
        DimensionDef d = new DimensionDef();
        d.setName(name);
        d.setExpression(expression);
        d.setCertified(certified ? Boolean.TRUE : null);
        return d;
    }

    /** LLM 输入切片：禁用字段被剔除、其余字段保留、原对象不被污染（提示词构造不碰共享上下文）；
     *  无禁用列的实体原样保留（引用相等，免无谓拷贝） */
    @Test
    public void llmInputStripsDisabledFields() {
        EntityDef ent = entity("ht_info",
                field("amt", "metric", false),
                field("secret_col", "dimension", true),
                field("dept", "dimension", false));
        EntityDef clean = entity("ht_pay", field("id", "ignore", false));
        List<EntityDef> src = Arrays.asList(ent, clean);

        List<EntityDef> out = EntityDef.forLlmInput(src);

        Assert.assertEquals(2, out.size());
        Assert.assertEquals("禁用字段剔除，其余保留", 2, out.get(0).getFields().size());
        Assert.assertTrue(out.get(0).getFields().stream().noneMatch(f -> "secret_col".equals(f.getName())));
        Assert.assertEquals("原对象不被污染", 3, ent.getFields().size());
        Assert.assertSame("无禁用列实体原样保留", clean, out.get(1));
    }

    /** 维度派生：禁用列即使是 dimension 角色也不派生维度（问数侧不可见），普通列照常派生 */
    @Test
    public void deriveSkipsDisabledColumn() {
        EntityDef ent = entity("ht_info",
                field("dept", "dimension", false),
                field("secret_col", "dimension", true));

        List<DimensionDef> dims = DimensionDeriveSupport.deriveFromEntities(
                Collections.singletonList(ent), new HashSet<>());

        Assert.assertEquals("禁用列不派生维度", 1, dims.size());
        Assert.assertEquals("dw.ht_info.dept", dims.get(0).getExpression());
    }

    /** 骨架链清理：禁用列派生的未认证维度被移除，认证维度保留（人工结论不动）；
     *  背景：骨架首派在 carry disabled 之前，残留维度需按禁用清单回收 */
    @Test
    public void dropDisabledColumnDimsKeepsCertified() {
        EntityDef ent = entity("ht_info",
                field("secret_col", "dimension", true),
                field("dept", "dimension", false));
        List<DimensionDef> dims = new ArrayList<>(Arrays.asList(
                dim("secret_col", "dw.ht_info.secret_col", false),
                dim("secret_col_cert", "dw.ht_info.secret_col", true),
                dim("dept", "dw.ht_info.dept", false)));

        DimensionDeriveSupport.dropDisabledColumnDims(dims, Collections.singletonList(ent));

        Assert.assertEquals("未认证残留维度移除、认证维度与普通维度保留", 2, dims.size());
        Assert.assertTrue(dims.stream().anyMatch(d -> "secret_col_cert".equals(d.getName())));
        Assert.assertTrue(dims.stream().anyMatch(d -> "dept".equals(d.getName())));
    }
}
